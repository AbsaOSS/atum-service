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

CREATE OR REPLACE FUNCTION runs.link_partitioning_to_parents(
    IN  i_id_partitioning           BIGINT,
    IN  i_parent_partitioning_ids   BIGINT[],
    IN  i_by_user                   TEXT,
    IN  i_copy_measure_definitions  BOOLEAN DEFAULT false,
    OUT status                      INTEGER,
    OUT status_text                 TEXT
) RETURNS record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs.link_partitioning_to_parents(4)
--      Links an existing partitioning to one or more parent partitionings atomically.
--      Serves POST /api/v2/partitionings/{partitioningId}/parents.
--
-- Parameters:
--      i_id_partitioning           - id of the child partitioning to link
--      i_parent_partitioning_ids   - ids of the parents to link under
--      i_by_user                   - user performing the link operation
--      i_copy_measure_definitions  - whether to copy measure definitions from parents
--
-- Status codes:
--      11 - OK (new links/edges written)
--      14 - Nothing to do (already linked)
--      32 - Parent partitioning would create a cycle
--      33 - Partitioning already has descendants and cannot be linked under new parents
--      41 - Child partitioning not found
--      42 - Parent partitioning not found
--
-------------------------------------------------------------------------------
DECLARE
    _parent_ids         BIGINT[];
    _link_status        INTEGER;
    _link_status_text   TEXT;
BEGIN
    -- Check for child partitioning existence
    PERFORM 1 FROM runs.partitionings WHERE id_partitioning = i_id_partitioning;
    IF NOT FOUND THEN
        status := 41;
        status_text := 'Child partitioning not found: ' || i_id_partitioning::TEXT;
        RETURN;
    END IF;

    -- Normalize parents: distinct, non-NULL, deterministic order
    _parent_ids := ARRAY(
        SELECT DISTINCT PID.id
        FROM unnest(i_parent_partitioning_ids) AS PID(id)
        WHERE PID.id IS NOT NULL
        ORDER BY PID.id
    );

    -- Check existence of all supplied parents
    PERFORM 1
    FROM unnest(_parent_ids) AS PID(id)
    WHERE NOT EXISTS (
        SELECT 1 FROM runs.partitionings P WHERE P.id_partitioning = PID.id
    );
    IF FOUND THEN
        status := 42;
        status_text := 'Parent partitioning not found';
        RETURN;
    END IF;

    -- Delegate linking to flows._link_to_parents
    SELECT LTP.status, LTP.status_text
    FROM flows._link_to_parents(i_id_partitioning, _parent_ids, i_by_user) AS LTP
    INTO _link_status, _link_status_text;

    IF _link_status IN (32, 33) THEN
        status := _link_status;
        status_text := _link_status_text;
        RETURN;
    END IF;

    -- Optional copy of measure definitions from parents
    IF COALESCE(i_copy_measure_definitions, false) AND cardinality(_parent_ids) > 0 THEN
        INSERT INTO runs.measure_definitions (fk_partitioning, measure_name, measured_columns, created_by)
        SELECT DISTINCT i_id_partitioning, MD.measure_name, MD.measured_columns, i_by_user
        FROM runs.measure_definitions MD
        WHERE MD.fk_partitioning = ANY(_parent_ids)
        ON CONFLICT ON CONSTRAINT measure_definitions_unq DO NOTHING;
    END IF;

    status := _link_status;
    status_text := _link_status_text;
    RETURN;
END;
$$ LANGUAGE plpgsql VOLATILE SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp;

ALTER FUNCTION runs.link_partitioning_to_parents(BIGINT, BIGINT[], TEXT, BOOLEAN) OWNER TO atum_owner;
REVOKE EXECUTE ON FUNCTION runs.link_partitioning_to_parents(BIGINT, BIGINT[], TEXT, BOOLEAN) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION runs.link_partitioning_to_parents(BIGINT, BIGINT[], TEXT, BOOLEAN) TO atum_user;

COMMENT ON FUNCTION runs.link_partitioning_to_parents(BIGINT, BIGINT[], TEXT, BOOLEAN) IS
'Links an existing child partitioning under one or more parent partitionings.
Serves POST /api/v2/partitionings/{partitioningId}/parents endpoint.';
