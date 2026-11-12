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

-- Drop old version from flows schema
DROP FUNCTION IF EXISTS flows.get_partitioning_lineage_graph(BIGINT, TEXT, INTEGER);

CREATE OR REPLACE FUNCTION lineage.get_partitioning_lineage_graph(
    IN  i_partitioning_id   BIGINT,
    IN  i_direction         TEXT DEFAULT 'both',
    IN  i_max_depth         INTEGER DEFAULT 5,
    OUT status              INTEGER,
    OUT status_text         TEXT,
    OUT lineage_graph       JSONB
) RETURNS record AS
-------------------------------------------------------------------------------
--
-- Function: lineage.get_partitioning_lineage_graph(3)
--      Returns the complete lineage graph (nodes + edges) centered on a partitioning.
--      Enables single-roundtrip lineage visualization without the N+1 query problem.
--
-- Parameters:
--      i_partitioning_id - ID of the focal partitioning
--      i_direction       - 'both', 'upstream', or 'downstream' (default 'both')
--      i_max_depth       - Maximum traversal depth (1 to 20, default 5)
--
-- Returns:
--      status        - Status code (11 = OK, 41 = Not Found)
--      status_text   - Status text
--      lineage_graph - JSONB containing { primaryPartitioningId, depth, direction, nodes: [...], edges: [...] }
--
-- Migrated from flows.get_partitioning_lineage_graph in V0.11.0.6
-------------------------------------------------------------------------------
$$
DECLARE
    _direction  TEXT;
    _max_depth  INTEGER;
BEGIN
    -- Check if the primary partitioning exists
    PERFORM 1 FROM runs.partitionings WHERE id_partitioning = i_partitioning_id;
    IF NOT FOUND THEN
        status := 41;
        status_text := 'Partitioning not found';
        lineage_graph := NULL;
        RETURN;
    END IF;

    -- Normalize parameters
    _direction := lower(COALESCE(i_direction, 'both'));
    IF _direction NOT IN ('both', 'upstream', 'downstream') THEN
        _direction := 'both';
    END IF;

    _max_depth := COALESCE(i_max_depth, 5);
    IF _max_depth < 1 THEN
        _max_depth := 1;
    ELSIF _max_depth > 20 THEN
        _max_depth := 20;
    END IF;

    status := 11;
    status_text := 'OK';

    WITH RECURSIVE upstream_edges AS (
        -- Base case: direct parents
        SELECT E.fk_child_partitioning, E.fk_parent_partitioning, E.created_by, E.created_at, 1 AS depth
        FROM lineage.partitioning_edges E
        WHERE E.fk_child_partitioning = i_partitioning_id
          AND _direction IN ('both', 'upstream')
        UNION
        -- Recursive case: grandparents and beyond
        SELECT E.fk_child_partitioning, E.fk_parent_partitioning, E.created_by, E.created_at, U.depth + 1
        FROM lineage.partitioning_edges E
        JOIN upstream_edges U ON E.fk_child_partitioning = U.fk_parent_partitioning
        WHERE U.depth < _max_depth
    ),
    downstream_edges AS (
        -- Base case: direct children
        SELECT E.fk_child_partitioning, E.fk_parent_partitioning, E.created_by, E.created_at, 1 AS depth
        FROM lineage.partitioning_edges E
        WHERE E.fk_parent_partitioning = i_partitioning_id
          AND _direction IN ('both', 'downstream')
        UNION
        -- Recursive case: grandchildren and beyond
        SELECT E.fk_child_partitioning, E.fk_parent_partitioning, E.created_by, E.created_at, D.depth + 1
        FROM lineage.partitioning_edges E
        JOIN downstream_edges D ON E.fk_parent_partitioning = D.fk_child_partitioning
        WHERE D.depth < _max_depth
    ),
    all_edges AS (
        SELECT DISTINCT fk_child_partitioning, fk_parent_partitioning, created_by, created_at
        FROM (
            SELECT fk_child_partitioning, fk_parent_partitioning, created_by, created_at FROM upstream_edges
            UNION ALL
            SELECT fk_child_partitioning, fk_parent_partitioning, created_by, created_at FROM downstream_edges
        ) combined
    ),
    all_node_ids AS (
        SELECT i_partitioning_id AS id
        UNION
        SELECT fk_child_partitioning AS id FROM all_edges
        UNION
        SELECT fk_parent_partitioning AS id FROM all_edges
    ),
    nodes_json AS (
        SELECT COALESCE(
            jsonb_agg(
                jsonb_build_object(
                    'id', P.id_partitioning,
                    'partitioning', P.partitioning,
                    'author', P.created_by
                )
                ORDER BY P.id_partitioning
            ),
            '[]'::jsonb
        ) AS nodes
        FROM all_node_ids N
        JOIN runs.partitionings P ON P.id_partitioning = N.id
    ),
    edges_json AS (
        SELECT COALESCE(
            jsonb_agg(
                jsonb_build_object(
                    'parentPartitioningId', E.fk_parent_partitioning,
                    'childPartitioningId', E.fk_child_partitioning,
                    'linkedBy', E.created_by,
                    'linkedAt', E.created_at
                )
                ORDER BY E.fk_parent_partitioning, E.fk_child_partitioning
            ),
            '[]'::jsonb
        ) AS edges
        FROM all_edges E
    )
    SELECT jsonb_build_object(
        'primaryPartitioningId', i_partitioning_id,
        'depth', _max_depth,
        'direction', _direction,
        'nodes', N.nodes,
        'edges', E.edges
    )
    INTO lineage_graph
    FROM nodes_json N, edges_json E;

    RETURN;
END;
$$ LANGUAGE plpgsql STABLE SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp;

ALTER FUNCTION lineage.get_partitioning_lineage_graph(BIGINT, TEXT, INTEGER) OWNER TO atum_owner;
REVOKE EXECUTE ON FUNCTION lineage.get_partitioning_lineage_graph(BIGINT, TEXT, INTEGER) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION lineage.get_partitioning_lineage_graph(BIGINT, TEXT, INTEGER) TO atum_user;

COMMENT ON FUNCTION lineage.get_partitioning_lineage_graph(BIGINT, TEXT, INTEGER) IS
'Returns the lineage graph (nodes + edges) for a partitioning.
Used by GET /api/v2/partitionings/{id}/lineage endpoint.
Migrated from flows.get_partitioning_lineage_graph in V0.11.0.6';
