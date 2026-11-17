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
--      BFS traversal is capped at 5000 nodes per direction (safety cap against giant fanout hubs).
--      Includes boundary/truncation hints:
--        * truncated: true when the MAX_NODES safety cap cut off further reachable nodes
--        * expandableUpstreamNodeIds: nodes in the returned subgraph that have parents outside it
--        * expandableDownstreamNodeIds: nodes in the returned subgraph that have children outside it
--
-- Parameters:
--      i_partitioning_id - ID of the focal partitioning
--      i_direction       - 'both', 'upstream', or 'downstream' (default 'both')
--      i_max_depth       - Maximum traversal depth (1 to 20, default 5)
--
-- Returns:
--      status        - Status code (11 = OK, 41 = Not Found)
--      status_text   - Status text
--      lineage_graph - JSONB containing { primaryPartitioningId, depth, direction, nodes, edges,
--                        truncated, expandableUpstreamNodeIds, expandableDownstreamNodeIds }
--
-------------------------------------------------------------------------------
$$
DECLARE
    MAX_NODES           CONSTANT INTEGER := 5000;
    _direction          TEXT;
    _max_depth          INTEGER;
    _upstream_visited   BIGINT[];
    _downstream_visited BIGINT[];
    _all_visited        BIGINT[];
    _frontier           BIGINT[];
    _next_frontier      BIGINT[];
    _depth              INTEGER;
    _remaining          INTEGER;
    _upstream_truncated BOOLEAN := FALSE;
    _downstream_truncated BOOLEAN := FALSE;
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
    _direction := lower(btrim(COALESCE(i_direction, 'both')));
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

    -- Iterative BFS for upstream traversal (guarantees each node expanded once, O(V+E), capped at MAX_NODES)
    _upstream_visited := ARRAY[]::BIGINT[];
    IF _direction IN ('both', 'upstream') THEN
        _upstream_visited := ARRAY[i_partitioning_id];
        _frontier := ARRAY[i_partitioning_id];
        _depth := 0;

        WHILE _depth < _max_depth AND cardinality(_upstream_visited) < MAX_NODES LOOP
            _depth := _depth + 1;

            SELECT COALESCE(
                array_agg(DISTINCT E.fk_parent_partitioning ORDER BY E.fk_parent_partitioning),
                ARRAY[]::BIGINT[]
            )
            INTO _next_frontier
            FROM lineage.partitioning_edges E
            WHERE E.fk_child_partitioning = ANY(_frontier)
              AND NOT EXISTS (
                  SELECT 1
                  FROM unnest(_upstream_visited) AS V(id)
                  WHERE V.id = E.fk_parent_partitioning
              );

            EXIT WHEN cardinality(_next_frontier) = 0;

            _remaining := MAX_NODES - cardinality(_upstream_visited);
            IF cardinality(_next_frontier) > _remaining THEN
                _next_frontier := _next_frontier[1:_remaining];
                _upstream_truncated := TRUE;
            END IF;

            _upstream_visited := _upstream_visited || _next_frontier;
            _frontier := _next_frontier;
        END LOOP;

        IF NOT _upstream_truncated AND _depth < _max_depth AND cardinality(_upstream_visited) >= MAX_NODES THEN
            _upstream_truncated := EXISTS (
                SELECT 1
                FROM lineage.partitioning_edges E
                JOIN runs.partitionings P ON P.id_partitioning = E.fk_parent_partitioning
                WHERE E.fk_child_partitioning = ANY(_frontier)
                  AND NOT (E.fk_parent_partitioning = ANY(_upstream_visited))
            );
        END IF;
    END IF;

    -- Iterative BFS for downstream traversal (capped at MAX_NODES)
    _downstream_visited := ARRAY[]::BIGINT[];
    IF _direction IN ('both', 'downstream') THEN
        _downstream_visited := ARRAY[i_partitioning_id];
        _frontier := ARRAY[i_partitioning_id];
        _depth := 0;

        WHILE _depth < _max_depth AND cardinality(_downstream_visited) < MAX_NODES LOOP
            _depth := _depth + 1;

            SELECT COALESCE(
                array_agg(DISTINCT E.fk_child_partitioning ORDER BY E.fk_child_partitioning),
                ARRAY[]::BIGINT[]
            )
            INTO _next_frontier
            FROM lineage.partitioning_edges E
            WHERE E.fk_parent_partitioning = ANY(_frontier)
              AND NOT EXISTS (
                  SELECT 1
                  FROM unnest(_downstream_visited) AS V(id)
                  WHERE V.id = E.fk_child_partitioning
              );

            EXIT WHEN cardinality(_next_frontier) = 0;

            _remaining := MAX_NODES - cardinality(_downstream_visited);
            IF cardinality(_next_frontier) > _remaining THEN
                _next_frontier := _next_frontier[1:_remaining];
                _downstream_truncated := TRUE;
            END IF;

            _downstream_visited := _downstream_visited || _next_frontier;
            _frontier := _next_frontier;
        END LOOP;

        IF NOT _downstream_truncated AND _depth < _max_depth AND cardinality(_downstream_visited) >= MAX_NODES THEN
            _downstream_truncated := EXISTS (
                SELECT 1
                FROM lineage.partitioning_edges E
                JOIN runs.partitionings P ON P.id_partitioning = E.fk_child_partitioning
                WHERE E.fk_parent_partitioning = ANY(_frontier)
                  AND NOT (E.fk_child_partitioning = ANY(_downstream_visited))
            );
        END IF;
    END IF;

    _all_visited := ARRAY(
        SELECT DISTINCT V.id
        FROM unnest(_upstream_visited || _downstream_visited) AS V(id)
    );

    -- Collect valid nodes (guarding against dangling edges), all induced edges, and unexpanded boundary nodes
    WITH valid_nodes AS (
        SELECT P.id_partitioning AS id, P.partitioning, P.created_by
        FROM unnest(_all_visited) AS V(id)
        JOIN runs.partitionings P ON P.id_partitioning = V.id
    ),
    all_edges AS (
        SELECT E.fk_child_partitioning, E.fk_parent_partitioning, E.created_by, E.created_at
        FROM valid_nodes VC
        JOIN lineage.partitioning_edges E ON E.fk_child_partitioning = VC.id
        JOIN valid_nodes VP ON VP.id = E.fk_parent_partitioning
    ),
    expandable_upstream AS (
        SELECT COALESCE(jsonb_agg(N.id ORDER BY N.id), '[]'::jsonb) AS ids
        FROM valid_nodes N
        WHERE EXISTS (
            SELECT 1
            FROM lineage.partitioning_edges E
            JOIN runs.partitionings P ON P.id_partitioning = E.fk_parent_partitioning
            WHERE E.fk_child_partitioning = N.id
              AND NOT EXISTS (SELECT 1 FROM valid_nodes V WHERE V.id = E.fk_parent_partitioning)
        )
    ),
    expandable_downstream AS (
        SELECT COALESCE(jsonb_agg(N.id ORDER BY N.id), '[]'::jsonb) AS ids
        FROM valid_nodes N
        WHERE EXISTS (
            SELECT 1
            FROM lineage.partitioning_edges E
            JOIN runs.partitionings P ON P.id_partitioning = E.fk_child_partitioning
            WHERE E.fk_parent_partitioning = N.id
              AND NOT EXISTS (SELECT 1 FROM valid_nodes V WHERE V.id = E.fk_child_partitioning)
        )
    ),
    nodes_json AS (
        SELECT COALESCE(
            jsonb_agg(
                jsonb_build_object(
                    'id', N.id,
                    'partitioning', N.partitioning,
                    'author', N.created_by
                )
                ORDER BY N.id
            ),
            '[]'::jsonb
        ) AS nodes
        FROM valid_nodes N
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
        'edges', E.edges,
        'truncated', (_upstream_truncated OR _downstream_truncated),
        'expandableUpstreamNodeIds', EU.ids,
        'expandableDownstreamNodeIds', ED.ids
    )
    INTO lineage_graph
    FROM nodes_json N, edges_json E, expandable_upstream EU, expandable_downstream ED;

    RETURN;
END;
$$ LANGUAGE plpgsql STABLE SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp;

ALTER FUNCTION lineage.get_partitioning_lineage_graph(BIGINT, TEXT, INTEGER) OWNER TO atum_owner;
REVOKE EXECUTE ON FUNCTION lineage.get_partitioning_lineage_graph(BIGINT, TEXT, INTEGER) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION lineage.get_partitioning_lineage_graph(BIGINT, TEXT, INTEGER) TO atum_user;

COMMENT ON FUNCTION lineage.get_partitioning_lineage_graph(BIGINT, TEXT, INTEGER) IS
'Returns the lineage graph (nodes, edges, and boundary/truncation hints) for a partitioning.';
