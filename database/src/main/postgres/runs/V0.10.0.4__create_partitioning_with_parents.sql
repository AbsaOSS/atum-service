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
    IN  i_copy_measure_definitions  BOOLEAN,
    OUT status                      INTEGER,
    OUT status_text                 TEXT,
    OUT id_partitioning             BIGINT,
    OUT created_by                  TEXT,
    OUT was_created                 BOOLEAN
) RETURNS record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs.create_partitioning_with_parents(4)
--      Creates a partitioning entry, or reuses the existing one, and links it to all the given parent partitionings
--      (zero, one or many) within a single transaction. It is a separate function (not an overload of
--      runs.create_partitioning), so that no existing call of runs.create_partitioning can become ambiguous.
--
--      An already existing partitioning is not reported as an error: the call is idempotent, the supplied
--      parents are linked to it (if they are not linked yet) and the id and the STORED author (created_by) of the
--      existing partitioning are returned, never the caller.
--
--      Linking (new and existing partitioning alike) is delegated to flows._link_to_parents: a self-link or a
--      cycle is rejected with status 32 and an existing partitioning that already has descendants is refused with
--      status 33 (a link that is not needed yet is no error, see below) before anything is written; when every
--      parent is already an ancestor the call takes a lock-free fast path; otherwise it takes the global advisory
--      lock, re-checks and adds the partitioning (a leaf) to the flows of the parents. Descendants are never
--      propagated. A partitioning created by this call is always a leaf, so status 33 can only happen for an
--      existing one.
--
--      Measure definitions: copied only when i_copy_measure_definitions is true (NULL means false), only when
--      the partitioning is newly created by this call, as the distinct union over all the supplied parents by
--      (measure_name, measured_columns). The copies get created_by = the caller (i_by_user) and created_at = now(),
--      not the parent's. The order of measured_columns is significant ({a,b} is not the same as {b,a}).
--
--      Additional data is NEVER copied.
--
-- Parameters:
--      i_partitioning              - partitioning to create or to link to the parents
--      i_by_user                   - user behind the change
--      i_parent_partitioning_ids   - ids of the parent partitionings; NULL elements and duplicates are
--                                    ignored; NULL or empty array means no parents
--      i_copy_measure_definitions  - copy the measure definitions of the parents to a newly created
--                                    partitioning; NULL is treated as false (no DEFAULT on purpose: a missing
--                                    bind must fail loudly)
--
-- Returns:
--      status              - Status code
--      status_text         - Status text
--      id_partitioning     - id of the partitioning (NULL if the call failed)
--      created_by          - author of the partitioning: the caller if it was created by this call, otherwise
--                            the stored author (NULL if the call failed)
--      was_created         - true if this call created the partitioning, false if it already existed
--                            (NULL if the call failed)
--
-- Status codes:
--      11                  - Partitioning created
--      12                  - Partitioning created with parent partitioning
--      13                  - Partitioning parents registered (an existing partitioning got new links)
--      14                  - Partitioning already present (nothing was written)
--      32                  - Parent partitioning would create a cycle (is the partitioning itself or its
--                            descendant); the text names the offending ids, nothing was written
--      33                  - An existing partitioning already has descendants and would get a new parent
--                            (nothing was written, no measure definitions are copied)
--      41                  - Parent partitioning not found (nothing was written)
--
-------------------------------------------------------------------------------
DECLARE
    _parent_ids             BIGINT[];
    _child_id               BIGINT;
    _child_created          BOOLEAN;
    _link_status            INTEGER;
    _link_status_text       TEXT;
BEGIN
    -- normalize parents: distinct, non-NULL, deterministic order
    _parent_ids := ARRAY(
        SELECT DISTINCT PID.id
        FROM unnest(i_parent_partitioning_ids) AS PID(id)
        WHERE PID.id IS NOT NULL
        ORDER BY PID.id
    );

    -- check existence of all parents before the first write (all-or-nothing)
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
    created_by := NULL;
    was_created := NULL;

    _child_id := runs._get_id_partitioning(i_partitioning, false);
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
            _child_id := runs._get_id_partitioning(i_partitioning, false);
            IF _child_id IS NULL THEN
                RAISE EXCEPTION 'Partitioning vanished while being created concurrently';
            END IF;
        END IF;
    END IF;

    -- all linking code (self-link / cycle / has-descendants rejection, lock-free fast path, global advisory lock)
    -- lives in the shared helper; it writes nothing when it returns 32 or 33
    SELECT LTP.status, LTP.status_text
    FROM flows._link_to_parents(_child_id, _parent_ids, i_by_user) AS LTP
    INTO _link_status, _link_status_text;

    IF _link_status IN (32, 33) THEN
        status := _link_status;
        status_text := _link_status_text;
        RETURN;
    END IF;

    -- measure definitions are copied only to a newly created partitioning and only when asked for; the distinct
    -- union over the parents by (measure_name, measured_columns); created_at is the column default now()
    IF _child_created AND COALESCE(i_copy_measure_definitions, false) AND cardinality(_parent_ids) > 0 THEN
        INSERT INTO runs.measure_definitions (fk_partitioning, measure_name, measured_columns, created_by)
        SELECT DISTINCT _child_id, MD.measure_name, MD.measured_columns, i_by_user
        FROM runs.measure_definitions MD
        WHERE MD.fk_partitioning = ANY(_parent_ids)
        ON CONFLICT ON CONSTRAINT measure_definitions_unq DO NOTHING;
    END IF;

    id_partitioning := _child_id;
    was_created := _child_created;

    IF _child_created THEN
        created_by := i_by_user;
    ELSE
        -- the author of an existing partitioning is the stored one, never the caller
        SELECT P.created_by
        FROM runs.partitionings P
        WHERE P.id_partitioning = _child_id
        INTO created_by;
    END IF;

    IF _child_created THEN
        IF cardinality(_parent_ids) = 0 THEN
            status := 11;
            status_text := 'Partitioning created';
        ELSE
            status := 12;
            status_text := 'Partitioning created with parent partitioning';
        END IF;
    -- so the child partitioning (=i_partitioning) existed already
    ELSIF _link_status = 11 THEN
        status := 13;
        status_text := 'Partitioning parents registered';
    ELSE
        status := 14;
        status_text := 'Partitioning already present';
    END IF;

    RETURN;
END;
$$
-- search_path is pinned (pg_catalog first, so built-in functions and operators cannot be shadowed).
-- public is required because the shipped flows._create_flow and the column defaults call public.global_id(), which
-- resolves its sequence through the search_path of the running function; pg_temp is last so temporary
-- objects cannot shadow. All other references in this function are schema-qualified.
LANGUAGE plpgsql VOLATILE SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp;

ALTER FUNCTION runs.create_partitioning_with_parents(JSONB, TEXT, BIGINT[], BOOLEAN) OWNER TO atum_owner;
REVOKE EXECUTE ON FUNCTION runs.create_partitioning_with_parents(JSONB, TEXT, BIGINT[], BOOLEAN) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION runs.create_partitioning_with_parents(JSONB, TEXT, BIGINT[], BOOLEAN) TO atum_user;
