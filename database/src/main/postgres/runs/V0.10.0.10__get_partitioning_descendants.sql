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

CREATE OR REPLACE FUNCTION runs.get_partitioning_descendants(
    IN i_id_partitioning BIGINT,
    IN i_limit INT DEFAULT 10,
    IN i_offset BIGINT DEFAULT 0,
    OUT status INTEGER,
    OUT status_text TEXT,
    OUT descendant_id BIGINT,
    OUT partitioning JSONB,
    OUT author TEXT,
    OUT has_more BOOLEAN
) RETURNS SETOF record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs.get_partitioning_descendants(3)
--      Returns the ids and partitionings of all (transitive) descendants of the given partitioning, i.e. all
--      members of the flows the partitioning is primary for, excluding the partitioning itself.
--      Downstream counterpart of runs.get_partitioning_ancestors.
--
-- Parameters:
--      i_id_partitioning   - id that we're asking the descendants for
--      i_limit             - (optional) maximum number of partitionings to return, default is 10
--      i_offset            - (optional) offset to use for pagination, default is 0
--
-- Returns:
--      status              - Status code
--      status_text         - Status message
--      descendant_id       - ID of descendant partitioning
--      partitioning        - partitioning data of descendant
--      author              - author of the descendant partitioning
--      has_more            - Flag indicating if there are more partitionings available
--
-- Status codes:
--      10 - OK
--      14 - OK (No descendants found, therefore no op)
--      41 - Partitioning not found
--
-------------------------------------------------------------------------------
DECLARE
    _has_more BOOLEAN;

BEGIN

    PERFORM 1 FROM runs.partitionings WHERE id_partitioning = i_id_partitioning;
    IF NOT FOUND THEN
        status := 41;
        status_text := 'Partitioning not found';
        RETURN NEXT;
        RETURN;
    END IF;

    IF i_limit IS NOT NULL THEN
        SELECT count(*) > i_limit
        FROM (SELECT 1
              FROM (SELECT DISTINCT PF.fk_partitioning
                    FROM flows.flows F
                             INNER JOIN flows.partitioning_to_flow PF ON PF.fk_flow = F.id_flow
                    WHERE F.fk_primary_partitioning = i_id_partitioning
                      AND PF.fk_partitioning IS DISTINCT FROM i_id_partitioning) D
              LIMIT i_limit + 1 OFFSET i_offset) s
        INTO _has_more;
    ELSE
        _has_more := false;
    END IF;

    RETURN QUERY
        SELECT 10                AS status,
               'OK'              AS status_text,
               P.id_partitioning AS descendant_id,
               P.partitioning    AS partitioning,
               P.created_by      AS author,
               _has_more         AS has_more
        FROM (SELECT DISTINCT PF.fk_partitioning
              FROM flows.flows F
                       INNER JOIN flows.partitioning_to_flow PF ON PF.fk_flow = F.id_flow
              WHERE F.fk_primary_partitioning = i_id_partitioning
                AND PF.fk_partitioning IS DISTINCT FROM i_id_partitioning) D
                 INNER JOIN runs.partitionings P ON P.id_partitioning = D.fk_partitioning
        ORDER BY P.id_partitioning
        LIMIT i_limit OFFSET i_offset;

    IF NOT FOUND THEN
        status := 14;
        status_text := 'OK';
        RETURN NEXT;
    END IF;
    RETURN;

END;
$$
    LANGUAGE plpgsql STABLE
                     SECURITY DEFINER
                     SET search_path = pg_catalog, public, pg_temp;

ALTER FUNCTION runs.get_partitioning_descendants(BIGINT, INT, BIGINT) OWNER TO atum_owner;
REVOKE EXECUTE ON FUNCTION runs.get_partitioning_descendants(BIGINT, INT, BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION runs.get_partitioning_descendants(BIGINT, INT, BIGINT) TO atum_user;
