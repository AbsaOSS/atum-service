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

CREATE OR REPLACE FUNCTION runs.get_partitioning_ancestors(
    IN i_id_partitioning    BIGINT,
    IN i_limit              INT DEFAULT 10,
    IN i_offset             BIGINT DEFAULT 0,
    OUT status              INTEGER,
    OUT status_text         TEXT,
    OUT ancestor_id         BIGINT,
    OUT partitioning        JSONB,
    OUT author              TEXT,
    OUT has_more            BOOLEAN
)  RETURNS SETOF record AS
$$
    -------------------------------------------------------------------------------
--
-- Function: runs.get_partitioning_ancestors(3)
--      Returns the ids and partitionings of the ancestors of the given partition id.
--
-- Parameters:
--      i_id_partitioning   - id that we're asking the Ancestors for
--      i_limit             - (optional) maximum number of partitionings to return, default is 10
--      i_offset            - (optional) offset to use for pagination, default is 0
--
-- Returns:
--      status              - Status code
--      status_text         - Status message
--      ancestor_id         - ID of Ancestor partitioning
--      partitioning        - partitioning data of ancestor
--      author              - author of the Ancestor partitioning
--      has_more            - Flag indicating if there are more partitionings available

-- Status codes:
--      10 - OK
--      14 - OK (No Ancestors found, therefore no op)
--      41 - Partitioning not found
--
-------------------------------------------------------------------------------
DECLARE
    _has_more BOOLEAN;

BEGIN

    -- Check if the partitioning exists
    PERFORM 1 FROM runs.partitionings WHERE id_partitioning = i_id_partitioning;
    IF NOT FOUND THEN
        status := 41;
        status_text := 'Partitioning not found';
        RETURN NEXT;
        RETURN;
    END IF;

    -- Check if there are more partitionings than the limit
    SELECT count(*) > i_limit
    FROM
        flows.partitioning_to_flow PF
            INNER JOIN flows.flows F ON F.id_flow = PF.fk_flow
            INNER JOIN runs.partitionings P ON P.id_partitioning = F.fk_primary_partitioning
    WHERE
        PF.fk_partitioning = i_id_partitioning AND
        P.id_partitioning IS DISTINCT FROM i_id_partitioning
    LIMIT i_limit + 1 OFFSET i_offset
    INTO _has_more;

    -- Return the ancestors
    RETURN QUERY
        SELECT
            10 AS status,
            'OK' AS status_text,
            P.id_partitioning AS ancestor_id,
            P.partitioning AS partitioning,
            P.created_by AS author,
            _has_more AS has_more
        FROM
            flows.partitioning_to_flow PF
                INNER JOIN flows.flows F ON F.id_flow = PF.fk_flow
                INNER JOIN runs.partitionings P ON P.id_partitioning = F.fk_primary_partitioning
        WHERE
            PF.fk_partitioning = i_id_partitioning AND
            P.id_partitioning IS DISTINCT FROM i_id_partitioning
        ORDER BY P.id_partitioning
        LIMIT i_limit
        OFFSET i_offset;

    --If no ancestors found send an OK status
    IF NOT FOUND THEN
        status := 14;
        status_text := 'OK';
        RETURN NEXT;
    END IF;
    RETURN;

END;
$$
    LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

ALTER FUNCTION runs.get_partitioning_ancestors(BIGINT, INT, BIGINT) OWNER TO atum_owner;
GRANT EXECUTE ON FUNCTION runs.get_partitioning_ancestors(BIGINT, INT, BIGINT) TO atum_user;
