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
-------------------------------------------------------------------------------
$$
DECLARE
    _direction  TEXT;
    _max_depth  INTEGER;
    _upstream_visited   BIGINT[];
    _downstream_visited BIGINT[];
    _frontier           BIGINT[];
    _next_frontier      BIGINT[];
    _depth              INTEGER;
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

    -- Iterative BFS for upstream traversal (guarantees each node expanded once, O(V+E))
    _upstream_visited := ARRAY[]::BIGINT[];
    IF _direction IN ('both', 'upstream') THEN
        _upstream_visited := ARRAY[i_partitioning_id];
        _frontier := ARRAY[i_partitioning_id];
        _depth := 0;

        WHILE _depth < _max_depth LOOP
            _depth := _depth + 1;

            SELECT COALESCE(array_agg(DISTINCT E.fk_parent_partitioning), ARRAY[]::BIGINT[])
            INTO _next_frontier
            FROM lineage.partitioning_edges E
            WHERE E.fk_child_partitioning = ANY(_frontier)
              AND NOT (E.fk_parent_partitioning = ANY(_upstream_visited));

            EXIT WHEN cardinality(_next_frontier) = 0;

            _upstream_visited := _upstream_visited || _next_frontier;
            _frontier := _next_frontier;
        END LOOP;
    END IF;

    -- Iterative BFS for downstream traversal
    _downstream_visited := ARRAY[]::BIGINT[];
    IF _direction IN ('both', 'downstream') THEN
        _downstream_visited := ARRAY[i_partitioning_id];
        _frontier := ARRAY[i_partitioning_id];
        _depth := 0;

        WHILE _depth < _max_depth LOOP
            _depth := _depth + 1;

            SELECT COALESCE(array_agg(DISTINCT E.fk_child_partitioning), ARRAY[]::BIGINT[])
            INTO _next_frontier
            FROM lineage.partitioning_edges E
            WHERE E.fk_parent_partitioning = ANY(_frontier)
              AND NOT (E.fk_child_partitioning = ANY(_downstream_visited));

            EXIT WHEN cardinality(_next_frontier) = 0;

            _downstream_visited := _downstream_visited || _next_frontier;
            _frontier := _next_frontier;
        END LOOP;
    END IF;

    -- Collect edges from each direction's visited set
    WITH all_edges AS (
        SELECT E.fk_child_partitioning, E.fk_parent_partitioning, E.created_by, E.created_at
        FROM lineage.partitioning_edges E
        WHERE (_direction IN ('both', 'upstream') AND
               E.fk_child_partitioning = ANY(_upstream_visited) AND
               E.fk_parent_partitioning = ANY(_upstream_visited))
           OR (_direction IN ('both', 'downstream') AND
               E.fk_parent_partitioning = ANY(_downstream_visited) AND
               E.fk_child_partitioning = ANY(_downstream_visited))
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
'Returns the lineage graph (nodes + edges) for a partitioning.';
