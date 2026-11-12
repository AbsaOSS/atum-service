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

-- Backward compatibility wrapper for flows.get_direct_children provided at the end of this file

CREATE OR REPLACE FUNCTION lineage.get_direct_children(
    IN  i_partitioning_id   BIGINT,
    OUT status              INTEGER,
    OUT status_text         TEXT,
    OUT child_id            BIGINT,
    OUT child_partitioning  JSONB,
    OUT child_author        TEXT,
    OUT edge_created_by     TEXT,
    OUT edge_created_at     TIMESTAMP WITH TIME ZONE
) RETURNS SETOF record AS
-------------------------------------------------------------------------------
--
-- Function: lineage.get_direct_children(1)
--      Returns the direct (immediate) 1-hop children of a partitioning (downstream impact analysis).
--      Uses the lineage.partitioning_edges table with the partitioning_edges_parent_idx index.
--
-- Parameters:
--      i_partitioning_id - ID of the parent partitioning
--
-- Returns:
--      status              - Status code
--      status_text         - Status text
--      child_id            - ID of the child partitioning, results are ordered by this
--      child_partitioning  - Partitioning value (JSONB)
--      child_author        - Author who created the child partitioning
--      edge_created_by     - User who established the parent-child relationship
--      edge_created_at     - When the parent-child relationship was established
--
-- Status codes (per returned row):
--      11 - OK (the row carries one direct child)
--      41 - Partitioning not found
--
-- Row contract:
--      - Partitioning not found       -> exactly ONE row, status 41, child_* columns NULL
--      - Partitioning has no children -> ZERO rows (empty set, success)
--      - Partitioning has N children  -> N rows, status 11 each, one direct child per row
--
-- Migrated from flows.get_direct_children in V0.11.0.5
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

    -- Set status for successful query
    status := 11;
    status_text := 'OK';

    -- Return all direct children
    RETURN QUERY
    SELECT
        status,
        status_text,
        P.id_partitioning,
        P.partitioning,
        P.created_by,
        E.created_by,
        E.created_at
    FROM lineage.partitioning_edges AS E
    JOIN runs.partitionings AS P
      ON P.id_partitioning = E.fk_child_partitioning
    WHERE E.fk_parent_partitioning = i_partitioning_id
    ORDER BY P.id_partitioning;
END;
$$ LANGUAGE plpgsql STABLE SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp;

ALTER FUNCTION lineage.get_direct_children(BIGINT) OWNER TO atum_owner;
REVOKE EXECUTE ON FUNCTION lineage.get_direct_children(BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION lineage.get_direct_children(BIGINT) TO atum_user;

COMMENT ON FUNCTION lineage.get_direct_children(BIGINT) IS
'Returns the direct (immediate) children of a partitioning.
Used by GET /api/v2/partitionings/{id}/children endpoint.
Returns empty set if partitioning has no children (not an error).
Migrated from flows.get_direct_children in V0.11.0.5';

-------------------------------------------------------------------------------
-- Backward compatibility wrapper for flows.get_direct_children
-------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION flows.get_direct_children(
    IN  i_partitioning_id   BIGINT,
    OUT status              INTEGER,
    OUT status_text         TEXT,
    OUT child_id            BIGINT,
    OUT child_partitioning  JSONB,
    OUT child_author        TEXT,
    OUT edge_created_by     TEXT,
    OUT edge_created_at     TIMESTAMP WITH TIME ZONE
) RETURNS SETOF record AS
$$
BEGIN
    RETURN QUERY
    SELECT * FROM lineage.get_direct_children(i_partitioning_id);
END;
$$ LANGUAGE plpgsql STABLE SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp;

ALTER FUNCTION flows.get_direct_children(BIGINT) OWNER TO atum_owner;
REVOKE EXECUTE ON FUNCTION flows.get_direct_children(BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION flows.get_direct_children(BIGINT) TO atum_user;

COMMENT ON FUNCTION flows.get_direct_children(BIGINT) IS
'Deprecated wrapper: delegates to lineage.get_direct_children(BIGINT). Will be removed in v0.13.0.';
