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

DROP FUNCTION IF EXISTS flows.get_flow_checkpoints(
    i_flow_id BIGINT,
    i_checkpoints_limit INT,
    i_offset BIGINT,
    i_checkpoint_name TEXT
);

CREATE OR REPLACE FUNCTION flows.get_flow_checkpoints(
    IN  i_flow_id              BIGINT,
    IN  i_checkpoints_limit    INT DEFAULT 5,
    IN  i_offset               BIGINT DEFAULT 0,
    IN  i_checkpoint_name      TEXT DEFAULT NULL,
    OUT status                 INTEGER,
    OUT status_text            TEXT,
    OUT id_checkpoint          UUID,
    OUT checkpoint_name        TEXT,
    OUT checkpoint_author      TEXT,
    OUT measured_by_atum_agent BOOLEAN,
    OUT measure_name           TEXT,
    OUT measured_columns       TEXT[],
    OUT measurement_value      JSONB,
    OUT checkpoint_start_time  TIMESTAMP WITH TIME ZONE,
    OUT checkpoint_end_time    TIMESTAMP WITH TIME ZONE,
    OUT id_partitioning        BIGINT,
    OUT partitioning           JSONB,
    OUT partitioning_author    TEXT,
    OUT has_more               BOOLEAN
) RETURNS SETOF record AS
$$
--------------------------------------------------------------------------------------------------------------------
--
-- Function: flows.get_flow_checkpoints(4)
--      Retrieves all checkpoints (measures and their measurement details) related to a primary flow
--      associated with the input partitioning.
--
-- Note: a single row returned from this function doesn't contain all data related to a single checkpoint - it only
--     represents one measure associated with a checkpoint. So even if only a single checkpoint would be retrieved,
--     this function can potentially return multiple rows.
--
-- Note: checkpoints will be retrieved in ordered fashion, by checkpoint_time and id_checkpoint
--
-- Parameters:
--      i_partitioning_of_flow  - partitioning to use for identifying the flow associate with checkpoints
--                                that will be retrieved
--      i_checkpoints_limit     - (optional) maximum number of checkpoint to return, returns all of them if NULL
--      i_offset                - (optional) offset for checkpoints pagination
--      i_checkpoint_name       - (optional) if specified, returns data related to particular checkpoint's name
--
--      Note: i_checkpoint_limit and i_offset are used for pagination purposes;
--            checkpoints are ordered by process_start_time in descending order
--            and then by id_checkpoint in ascending order
--
-- Returns:
--      status                 - Status code
--      status_text            - Status text
--      id_checkpoint          - ID of retrieved checkpoint
--      checkpoint_name        - Name of the retrieved checkpoint
--      checkpoint_author      - Author of the checkpoint
--      measured_by_atum_agent - Flag indicating whether the checkpoint was measured by Atum Agent
--                               (if false, data supplied manually)
--      measure_name           - measure name associated with a given checkpoint
--      measured_columns       - measure columns associated with a given checkpoint
--      measurement_value      - measurement details associated with a given checkpoint
--      checkpoint_start_time  - Time of the checkpoint
--      checkpoint_end_time    - End time of the checkpoint computation
--      id_partitioning        - ID of the partitioning
--      partitioning         - Partitioning value
--      partitioning_author    - Author of the partitioning
--      has_more               - flag indicating whether there are more checkpoints available, always `false` if `i_limit` is NULL
--
-- Status codes:
--      11                     - OK
--      42                     - Flow not found
---------------------------------------------------------------------------------------------------
DECLARE
    _has_more BOOLEAN;
BEGIN
    -- Check if the flow exists by querying the partitioning_to_flow table.
    -- Rationale:
    -- This table is preferred over the flows table because:
    -- 1. Every flow has at least one record in partitioning_to_flow.
    -- 2. This table is used in subsequent queries, providing a caching advantage.
    -- 3. Improves performance by reducing the need to query the flows table directly.
    PERFORM 1 FROM flows.partitioning_to_flow WHERE fk_flow = i_flow_id;
    IF NOT FOUND THEN
        status := 42;
        status_text := 'Flow not found';
        RETURN NEXT;
        RETURN;
    END IF;

    -- Determine if there are more checkpoints than the limit
    IF i_checkpoints_limit IS NOT NULL THEN
        SELECT count(*) > i_checkpoints_limit
        FROM runs.checkpoints C
                 JOIN flows.partitioning_to_flow PF ON C.fk_partitioning = PF.fk_partitioning
        WHERE PF.fk_flow = i_flow_id
          AND (i_checkpoint_name IS NULL OR C.checkpoint_name = i_checkpoint_name)
        LIMIT i_checkpoints_limit + 1 OFFSET i_offset
        INTO _has_more;
    ELSE
        _has_more := false;
    END IF;

    -- Retrieve the checkpoints and their associated measurements
    RETURN QUERY
        WITH limited_checkpoints AS (
            SELECT C.id_checkpoint,
                   C.fk_partitioning,
                   C.checkpoint_name,
                   C.created_by,
                   C.measured_by_atum_agent,
                   C.process_start_time,
                   C.process_end_time
            FROM runs.checkpoints C
                     JOIN flows.partitioning_to_flow PF ON C.fk_partitioning = PF.fk_partitioning
            WHERE PF.fk_flow = i_flow_id
              AND (i_checkpoint_name IS NULL OR C.checkpoint_name = i_checkpoint_name)
            ORDER BY C.process_start_time desc
            LIMIT i_checkpoints_limit OFFSET i_offset
        )
        SELECT
            11 AS status,
            'OK' AS status_text,
            LC.id_checkpoint,
            LC.checkpoint_name,
            LC.created_by AS author,
            LC.measured_by_atum_agent,
            MD.measure_name,
            MD.measured_columns,
            M.measurement_value,
            LC.process_start_time AS checkpoint_start_time,
            LC.process_end_time AS checkpoint_end_time,
            LC.fk_partitioning AS id_partitioning,
            P.partitioning AS partitioning,
            P.created_by AS partitioning_author,
            _has_more AS has_more
        FROM
            limited_checkpoints LC
                INNER JOIN
            runs.measurements M ON LC.id_checkpoint = M.fk_checkpoint
                INNER JOIN
            runs.measure_definitions MD ON M.fk_measure_definition = MD.id_measure_definition
                INNER JOIN
            runs.partitionings P ON LC.fk_partitioning = P.id_partitioning
        ORDER BY LC.process_start_time desc;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION flows.get_flow_checkpoints(BIGINT, INT, BIGINT, TEXT) TO atum_owner;
