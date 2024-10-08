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

CREATE OR REPLACE FUNCTION flows.get_flow_partitionings(
    IN i_flow_id               BIGINT,
    IN i_limit                 INT DEFAULT 5,
    IN i_offset                BIGINT DEFAULT 0,
    OUT status                 INTEGER,
    OUT status_text            TEXT,
    OUT id                     BIGINT,
    OUT partitioning           JSONB,
    OUT author                 TEXT,
    OUT has_more               BOOLEAN
) RETURNS SETOF record AS
-------------------------------------------------------------------------------
--
-- Function: flows.get_flow_partitionings(3)
--      Retrieves all partitionings associated with the input flow.
--
-- Note: partitionings will be retrieved in ordered fashion, by created_at column from runs.partitionings table
--
-- Parameters:
--      i_flow_id               - flow id to use for identifying the partitionings that will be retrieved
--      i_limit                 - (optional) maximum number of partitionings to return, default is 5
--      i_offset                - (optional) offset to use for pagination, default is 0
--
-- Returns:
--      status                  - Status code
--      status_text             - Status text
--      id                      - ID of retrieved partitioning
--      partitioning            - Partitioning value
--      author                  - Author of the partitioning
--      has_more                - Flag indicating if there are more partitionings available
--
-- Status codes:
--      11 - OK
--      41 - Flow not found
--
-------------------------------------------------------------------------------
$$
DECLARE
    _has_more BOOLEAN;
BEGIN
    PERFORM 1 FROM flows.flows WHERE id_flow = i_flow_id;
    IF NOT FOUND THEN
        status := 41;
        status_text := 'Flow not found';
        RETURN NEXT;
        RETURN;
    END IF;

    IF i_limit IS NOT NULL THEN
        SELECT count(*) > i_limit
        FROM flows.partitioning_to_flow PTF
        WHERE PTF.fk_flow = i_flow_id
        LIMIT i_limit + 1 OFFSET i_offset
        INTO _has_more;
    ELSE
        _has_more := false;
    END IF;


    RETURN QUERY
        SELECT
            11 AS status,
            'OK' AS status_text,
            P.id_partitioning,
            P.partitioning,
            P.created_by,
            _has_more
        FROM
            runs.partitionings P INNER JOIN
            flows.partitioning_to_flow PF ON  PF.fk_partitioning = P.id_partitioning
        WHERE
            PF.fk_flow = i_flow_id
        ORDER BY
            P.id_partitioning,
            P.created_at DESC
        LIMIT i_limit OFFSET i_offset;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION flows.get_flow_partitionings(BIGINT, INT, BIGINT) TO atum_owner;
