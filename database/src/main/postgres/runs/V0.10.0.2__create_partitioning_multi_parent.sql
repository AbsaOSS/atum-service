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

CREATE OR REPLACE FUNCTION runs.create_partitioning(
    IN  i_partitioning              JSONB,
    IN  i_by_user                   TEXT,
    IN  i_parent_partitioning_ids   BIGINT[],
    OUT status                      INTEGER,
    OUT status_text                 TEXT,
    OUT id_partitioning             BIGINT
) RETURNS record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs.create_partitioning(3) - multi-parent overload
--      Creates a partitioning entry, or reuses the existing one, and links it to all the given parent
--      partitionings within a single transaction. Measure definitions and additional data of newly
--      linked parents are copied using the union + de-duplication policy of
--      runs._copy_parents_data_to_partitioning. An already existing partitioning is not reported as
--      an error; newly supplied parents are linked to it (idempotent re-link).
--      A supplied parent counts as newly linked when the partitioning is freshly created, or when,
--      before this call, the parent was not yet an ancestor of the existing partitioning (i.e. the
--      partitioning was not a member of any flow whose primary partitioning is that parent).
--
-- Parameters:
--      i_partitioning              - partitioning to create or to link to the parents
--      i_by_user                   - user behind the change
--      i_parent_partitioning_ids   - ids of the parent partitionings; NULL elements and duplicates are
--                                    ignored; NULL or empty array means no parents
--
-- Returns:
--      status              - Status code
--      status_text         - Status text (lists additional data keys skipped due to parent conflicts)
--      id_partitioning     - id of the partitioning
--
-- Status codes:
--      11                  - Partitioning created
--      12                  - Partitioning created with parent partitioning
--      13                  - Partitioning parents registered
--      14                  - Partitioning already present
--      41                  - Parent partitioning not found
--
-------------------------------------------------------------------------------
DECLARE
    _parent_ids             BIGINT[];
    _new_parent_ids         BIGINT[];
    _parent_id              BIGINT;
    _child_created          BOOLEAN;
    _skipped_ad_keys        TEXT[] := '{}'::TEXT[];
BEGIN
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

    id_partitioning := runs._get_id_partitioning(i_partitioning, true);
    _child_created := id_partitioning IS NULL;

    IF _child_created THEN
        INSERT INTO runs.partitionings (partitioning, created_by)
        VALUES (i_partitioning, i_by_user)
        RETURNING partitionings.id_partitioning INTO create_partitioning.id_partitioning;

        PERFORM 1 FROM flows._create_flow(id_partitioning, i_by_user);

        -- a new partitioning takes the data of every supplied parent
        _new_parent_ids := _parent_ids;
    ELSE
        -- an existing partitioning takes the data only of parents that are not yet its ancestors;
        -- determined BEFORE linking, because linking one parent can make another one an ancestor
        _new_parent_ids := ARRAY(
            SELECT PID.id
            FROM unnest(_parent_ids) AS PID(id)
            WHERE NOT EXISTS (
                SELECT 1
                FROM flows.partitioning_to_flow PTF
                INNER JOIN flows.flows F ON F.id_flow = PTF.fk_flow
                WHERE PTF.fk_partitioning = create_partitioning.id_partitioning
                  AND F.fk_primary_partitioning = PID.id
            )
            ORDER BY PID.id
        );
    END IF;

    -- link every parent; no short-circuit for an existing partitioning; idempotent per parent
    FOREACH _parent_id IN ARRAY _parent_ids LOOP
        PERFORM 1 FROM flows._add_to_parent_flows(_parent_id, id_partitioning, i_by_user);
    END LOOP;

    -- copy measure definitions and additional data from the newly linked parents
    IF cardinality(_new_parent_ids) > 0 THEN
        SELECT CPD.skipped_ad_keys
        FROM runs._copy_parents_data_to_partitioning(id_partitioning, _parent_ids, _new_parent_ids, i_by_user) AS CPD
        INTO _skipped_ad_keys;
    END IF;

    IF _child_created THEN
        IF cardinality(_parent_ids) = 0 THEN
            status := 11;
            status_text := 'Partitioning created';
        ELSE
            status := 12;
            status_text := 'Partitioning created with parent partitioning';
        END IF;
    ELSIF cardinality(_new_parent_ids) > 0 THEN
        status := 13;
        status_text := 'Partitioning parents registered';
    ELSE
        status := 14;
        status_text := 'Partitioning already present';
    END IF;

    IF cardinality(_skipped_ad_keys) > 0 THEN
        status_text := status_text || '; conflicting additional data skipped: ' || array_to_string(_skipped_ad_keys, ', ');
    END IF;

    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

ALTER FUNCTION runs.create_partitioning(JSONB, TEXT, BIGINT[]) OWNER TO atum_owner;
REVOKE EXECUTE ON FUNCTION runs.create_partitioning(JSONB, TEXT, BIGINT[]) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION runs.create_partitioning(JSONB, TEXT, BIGINT[]) TO atum_user;
