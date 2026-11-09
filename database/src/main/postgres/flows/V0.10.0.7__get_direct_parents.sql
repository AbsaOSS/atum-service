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

CREATE OR REPLACE FUNCTION flows.get_direct_parents(
    IN  i_partitioning_id   BIGINT,
    OUT status              INTEGER,
    OUT status_text         TEXT,
    OUT parent_id           BIGINT,
    OUT parent_partitioning JSONB,
    OUT parent_author       TEXT,
    OUT edge_created_by     TEXT,
    OUT edge_created_at     TIMESTAMP WITH TIME ZONE
) RETURNS SETOF record AS
-------------------------------------------------------------------------------
--
-- Function: flows.get_direct_parents(1)
--      Returns the direct (immediate) parents of a partitioning.
--      Uses the flows.partitioning_edges table for fast indexed lookup.
--
-- Parameters:
--      i_partitioning_id - ID of the child partitioning
--
-- Returns:
--      status              - Status code
--      status_text         - Status text
--      parent_id           - ID of the parent partitioning
--      parent_partitioning - Partitioning value (JSONB)
--      parent_author       - Author who created the parent partitioning
--      edge_created_by     - User who established the parent-child relationship
--      edge_created_at     - When the parent-child relationship was established
--
-- Status codes (per returned row):
--      11 - OK (the row carries one direct parent)
--      41 - Partitioning not found (single row, all parent_* columns NULL)
--
-- Row contract (mirrors flows.get_flow_partitionings):
--      - Partitioning not found      -> exactly ONE row, status 41, parent_* columns NULL
--      - Partitioning has no parents -> ZERO rows (empty set, which IS success - not a status row)
--      - Partitioning has N parents  -> N rows, status 11 each, one direct parent per row
--
--      Callers MUST treat an empty result set as "exists, has no parents" (e.g. HTTP 200 with an
--      empty list), never as an error. Only a status-41 row means not-found. Do NOT assume at least
--      one row is always returned. The fa-db DoobieMultipleResultFunctionWithAggStatus layer handles
--      this shape (see server GetFlowPartitionings for the matching read pattern).
--
-- Notes:
--      - Served by the primary-key index (leading column fk_child_partitioning); no extra index needed.
--      - Results ordered by parent ID for deterministic output.
--
-- Usage:
--      SELECT * FROM flows.get_direct_parents(123);
--
-------------------------------------------------------------------------------
$$
BEGIN
    -- Check if the partitioning exists
    PERFORM 1 FROM runs.partitionings WHERE id_partitioning = i_partitioning_id;
    IF NOT FOUND THEN
        status := 41;
        status_text := 'Partitioning not found';
        RETURN NEXT;
        RETURN;
    END IF;

    -- Set status for successful query (even if no parents exist)
    status := 11;
    status_text := 'OK';

    -- Return all direct parents
    RETURN QUERY
    SELECT
        status,
        status_text,
        P.id_partitioning,
        P.partitioning,
        P.created_by,
        E.created_by,
        E.created_at
    FROM flows.partitioning_edges E
    INNER JOIN runs.partitionings P ON P.id_partitioning = E.fk_parent_partitioning
    WHERE E.fk_child_partitioning = i_partitioning_id
    ORDER BY P.id_partitioning;
END;
$$ LANGUAGE plpgsql STABLE SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp;

ALTER FUNCTION flows.get_direct_parents(BIGINT) OWNER TO atum_owner;
GRANT EXECUTE ON FUNCTION flows.get_direct_parents(BIGINT) TO atum_owner;

COMMENT ON FUNCTION flows.get_direct_parents(BIGINT) IS
'Returns the direct (immediate) parents of a partitioning.
Used by GET /api/v2/partitionings/{id}/parents endpoint.
Returns empty set if partitioning has no parents (not an error).';
