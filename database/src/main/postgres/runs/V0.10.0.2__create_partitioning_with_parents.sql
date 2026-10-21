/*
 * Copyright 2021 ABSA Group Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

CREATE OR REPLACE FUNCTION runs.create_partitioning_with_parents(
    IN  i_partitioning              JSONB,
    IN  i_by_user                   TEXT,
    IN  i_parent_partitioning_ids   BIGINT[],
    OUT status                      INTEGER,
    OUT status_text                 TEXT,
    OUT id_partitioning             BIGINT,
    OUT skipped_ad_keys             TEXT[]
) RETURNS record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs.create_partitioning_with_parents(3)
--      Creates a partitioning entry, or reuses the existing one, and links it to all the given parent
--      partitionings within a single transaction. It is a separate function (not an overload of
--      runs.create_partitioning), so that no existing call of runs.create_partitioning can become
--      ambiguous.
--
--      An already existing partitioning is not reported as an error; the supplied parents are linked
--      to it (idempotent re-link).
--
--      Data copy: measure definitions and additional data of EVERY supplied parent are copied to the
--      partitioning using the union + de-duplication policy of runs._copy_parents_data_to_partitioning
--      (the partitioning's own data always wins, existing rows are never duplicated). The copy does not
--      depend on whether the parent was already an ancestor, so supplying {p1,p2} in one call and
--      {p1} followed by {p1,p2} end up with the same data.
--
--      Additional data conflicts: a key is skipped (and reported) when the values of that key differ
--      across ALL the ancestors of the partitioning after linking (every primary partitioning of the
--      flows the partitioning is a member of - the same set runs.get_partitioning_ancestors returns).
--      The scope is deliberately the full ancestor set and not only the parents of this call, so that a
--      key skipped for a conflict is not filled in later by a parent linked in another call. The
--      trade-off is that the check is conservative: a disagreement anywhere in the lineage (e.g. a
--      parent that overrode a key inherited from its own parent) skips the key rather than picking one
--      value. A value the partitioning already holds (own or inherited earlier) counts as its own.
--
--      Cycles: when the partitioning already exists, a supplied parent that is the partitioning itself
--      or one of its descendants is rejected with status 43 before anything is written. This is a
--      write-side guard only; it does not detect cycles that already exist (read-side lineage cycle
--      detection is a separate, deferred concern).
--
--      Descendants: when parents are linked to an existing partitioning, all its descendants are added
--      to the flows of the new parents as well, so the new parents are their ancestors too.
--
-- Parameters:
--      i_partitioning              - partitioning to create or to link to the parents
--      i_by_user                   - user behind the change
--      i_parent_partitioning_ids   - ids of the parent partitionings; NULL elements and duplicates are
--                                    ignored; NULL or empty array means no parents
--
-- Returns:
--      status              - Status code
--      status_text         - Status text
--      id_partitioning     - id of the partitioning (NULL if the call failed)
--      skipped_ad_keys     - additional data keys skipped due to conflicting ancestor values, sorted,
--                            never NULL
--
-- Status codes:
--      11                  - Partitioning created
--      12                  - Partitioning created with parent partitioning
--      13                  - Partitioning parents registered (an existing partitioning got new links
--                            and/or data copied)
--      14                  - Partitioning already present (nothing was written)
--      41                  - Parent partitioning not found
--      43                  - Parent partitioning would create a cycle (is the partitioning itself or
--                            its descendant)
--
-------------------------------------------------------------------------------
DECLARE
    _parent_ids             BIGINT[];
    _conflict_scope         BIGINT[];
    _parent_id              BIGINT;
    _child_id               BIGINT;
    _child_created          BOOLEAN;
    _changed                BOOLEAN := false;
    _copied_rows            INTEGER := 0;
    _rows                   INTEGER;
    _link_status            INTEGER;
BEGIN
    skipped_ad_keys := '{}'::TEXT[];

    -- normalize parents: distinct, non-NULL, deterministic order
    _parent_ids := ARRAY(
        SELECT DISTINCT PID.id
        FROM unnest(i_parent_partitioning_ids) AS PID(id)
        WHERE PID.id IS NOT NULL
        ORDER BY PID.id
    );

    -- validate all parents before the first write (all-or-nothing)
    PERFORM 1
    FROM unnest(_parent_ids) AS PID(id)
    WHERE NOT EXISTS (
        SELECT 1 FROM runs.partitionings P WHERE P.id_partitioning = PID.id
    );
    IF FOUND THEN
        status := 41;
        status_text := 'Parent partitioning not found';
        RETURN;
    END IF;

    id_partitioning := NULL;
    _child_id := runs._get_id_partitioning(i_partitioning, true);
    _child_created := false;

    IF _child_id IS NULL THEN
        -- concurrency-safe create: if another transaction creates the same partitioning at the same time,
        -- the insert waits for it, does nothing and the partitioning is then handled as an existing one
        INSERT INTO runs.partitionings (partitioning, created_by)
        VALUES (i_partitioning, i_by_user)
        ON CONFLICT ON CONSTRAINT partitioning_unq DO NOTHING
        RETURNING partitionings.id_partitioning INTO _child_id;

        IF _child_id IS NOT NULL THEN
            _child_created := true;
            PERFORM 1 FROM flows._create_flow(_child_id, i_by_user);
        ELSE
            _child_id := runs._get_id_partitioning(i_partitioning, true);
            IF _child_id IS NULL THEN
                RAISE EXCEPTION 'Partitioning vanished while being created concurrently';
            END IF;
        END IF;
    END IF;

    IF NOT _child_created AND cardinality(_parent_ids) > 0 THEN
        -- serializes the re-linking of existing partitionings, so that two concurrent calls cannot
        -- together create a cycle that neither of them can see; released at the end of the transaction
        PERFORM pg_advisory_xact_lock(hashtextextended('atum.runs.create_partitioning_with_parents', 0));

        -- a parent that is the partitioning itself or its descendant would create a cycle (a partitioning is a
        -- member of the flow it is primary for, and so are all its descendants); nothing has been written yet
        PERFORM 1
        FROM unnest(_parent_ids) AS PID(id)
        INNER JOIN flows.partitioning_to_flow PTF ON PTF.fk_partitioning = PID.id
        INNER JOIN flows.flows F ON F.id_flow = PTF.fk_flow
        WHERE F.fk_primary_partitioning = _child_id;
        IF FOUND THEN
            status := 43;
            status_text := 'Parent partitioning would create a cycle';
            RETURN;
        END IF;
    END IF;

    -- link every parent; no short-circuit for an existing partitioning; idempotent per parent
    FOREACH _parent_id IN ARRAY _parent_ids LOOP
        SELECT ATPF.status
        FROM flows._add_to_parent_flows(_parent_id, _child_id, i_by_user) AS ATPF
        INTO _link_status;
        IF _link_status = 11 THEN
            _changed := true;
        END IF;
    END LOOP;

    IF NOT _child_created AND cardinality(_parent_ids) > 0 THEN
        -- the descendants of the partitioning become reachable from the flows of the new ancestors too
        -- (a new partitioning has no descendants)
        INSERT INTO flows.partitioning_to_flow (fk_flow, fk_partitioning, created_by)
        SELECT CPTF.fk_flow, DPTF.fk_partitioning, i_by_user
        FROM flows.partitioning_to_flow CPTF
        INNER JOIN flows.flows CF ON CF.fk_primary_partitioning = _child_id
        INNER JOIN flows.partitioning_to_flow DPTF ON DPTF.fk_flow = CF.id_flow AND DPTF.fk_partitioning <> _child_id
        WHERE CPTF.fk_partitioning = _child_id
        ON CONFLICT DO NOTHING;
        GET DIAGNOSTICS _rows = ROW_COUNT;
        IF _rows > 0 THEN
            _changed := true;
        END IF;
    END IF;

    -- copy measure definitions and additional data from the supplied parents
    IF cardinality(_parent_ids) > 0 THEN
        -- every ancestor of the partitioning after linking
        _conflict_scope := ARRAY(
            SELECT DISTINCT F.fk_primary_partitioning
            FROM flows.partitioning_to_flow PTF
            INNER JOIN flows.flows F ON F.id_flow = PTF.fk_flow
            WHERE PTF.fk_partitioning = _child_id
              AND F.fk_primary_partitioning <> _child_id
        );

        SELECT CPD.skipped_ad_keys, CPD.copied_rows
        FROM runs._copy_parents_data_to_partitioning(_child_id, _conflict_scope, _parent_ids, i_by_user) AS CPD
        INTO skipped_ad_keys, _copied_rows;
    END IF;

    id_partitioning := _child_id;

    IF _child_created THEN
        IF cardinality(_parent_ids) = 0 THEN
            status := 11;
            status_text := 'Partitioning created';
        ELSE
            status := 12;
            status_text := 'Partitioning created with parent partitioning';
        END IF;
    ELSIF _changed OR _copied_rows > 0 THEN
        status := 13;
        status_text := 'Partitioning parents registered';
    ELSE
        status := 14;
        status_text := 'Partitioning already present';
    END IF;

    IF cardinality(skipped_ad_keys) > 0 THEN
        status_text := status_text || '; ' || cardinality(skipped_ad_keys)::TEXT
            || ' conflicting additional data key(s) skipped';
    END IF;

    RETURN;
END;
$$
-- search_path is pinned (pg_catalog first, so built-in functions and operators cannot be shadowed). public is
-- required because the shipped flows._create_flow and the column defaults call public.global_id(), which
-- resolves its sequence through the search_path of the running function; pg_temp is last so temporary
-- objects cannot shadow. All other references in this function are schema-qualified.
LANGUAGE plpgsql VOLATILE SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp;

ALTER FUNCTION runs.create_partitioning_with_parents(JSONB, TEXT, BIGINT[]) OWNER TO atum_owner;
REVOKE EXECUTE ON FUNCTION runs.create_partitioning_with_parents(JSONB, TEXT, BIGINT[]) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION runs.create_partitioning_with_parents(JSONB, TEXT, BIGINT[]) TO atum_user;
