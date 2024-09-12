/*
 * Copyright 2021 ABSA Group Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

CREATE OR REPLACE FUNCTION runs.get_partitioning_checkpoints(
    IN i_partitioning_id           BIGINT,
    IN i_limit                     INT DEFAULT 5,
    IN i_offset                    BIGINT DEFAULT 0,
    IN i_checkpoint_name           TEXT DEFAULT NULL,
    OUT status                     INTEGER,
    OUT status_text                TEXT,
    OUT id_checkpoint              UUID,
    OUT checkpoint_name            TEXT,
    OUT author                     TEXT,
    OUT measured_by_atum_agent     BOOLEAN,
    OUT measure_name               TEXT,
    OUT measured_columns           TEXT[],
    OUT measurement_value          JSONB,
    OUT checkpoint_start_time      TIMESTAMP WITH TIME ZONE,
    OUT checkpoint_end_time        TIMESTAMP WITH TIME ZONE,
    OUT has_more                   BOOLEAN
)
RETURNS SETOF record AS
-------------------------------------------------------------------------------
--
-- Function: runs.get_partitioning_checkpoints(4)
--      Retrieves checkpoints (measures and their measurement details) related to a
--      given partitioning (and checkpoint name, if specified).
--
-- Parameters:
--      i_partitioning          - partitioning of requested checkpoints
--      i_limit                 - (optional) maximum number of checkpoints to return
--                                i_limit relates to amount of checkpoints returned, not the amount of rows returned
--                                as usually there is more than one row per checkpoint
--      i_offset                - (optional) offset of the first checkpoint to return
--      i_checkpoint_name       - (optional) name of the checkpoint

-- Note: i_limit and i_offset are used for pagination purposes;
--       checkpoints are ordered by process_start_time in descending order
--       and then by id_checkpoint in ascending order
--
-- Returns:
--      status                  - Status code
--      status_text             - Status message
--      id_checkpoint           - ID of the checkpoint
--      checkpoint_name         - Name of the checkpoint
--      author                  - Author of the checkpoint
--      measured_by_atum_agent  - Flag indicating whether the checkpoint was measured by ATUM agent
--      measure_name            - Name of the measure
--      measure_columns         - Columns of the measure
--      measurement_value       - Value of the measurement
--      checkpoint_start_time   - Time of the checkpoint
--      checkpoint_end_time     - End time of the checkpoint computation
--      has_more                - Flag indicating whether there are more checkpoints available
--
-- Status codes:
--      11 - OK
--      41 - Partitioning not found
--      42 - No checkpoint data found
--
-------------------------------------------------------------------------------
$$
BEGIN
    PERFORM 1 FROM runs.partitionings WHERE id_partitioning = i_partitioning_id;
    IF NOT FOUND THEN
        status := 41;
        status_text := 'Partitioning not found';
        RETURN NEXT;
        RETURN;
    END IF;

    RETURN QUERY
        WITH limited_checkpoints AS (
            SELECT C.id_checkpoint,
                   C.process_start_time,
                   ROW_NUMBER() OVER (ORDER BY C.process_start_time DESC, C.id_checkpoint) AS rn
            FROM runs.checkpoints C
            WHERE C.fk_partitioning = i_partitioning_id
              AND (i_checkpoint_name IS NULL OR C.checkpoint_name = i_checkpoint_name)
            ORDER BY C.process_start_time DESC, C.id_checkpoint
            LIMIT i_limit + 1 OFFSET i_offset
        )
        SELECT
            11 AS status,
            'Ok' AS status_text,
            C.id_checkpoint,
            C.checkpoint_name,
            C.created_by AS author,
            C.measured_by_atum_agent,
            md.measure_name,
            md.measured_columns,
            M.measurement_value,
            C.process_start_time AS checkpoint_start_time,
            C.process_end_time AS checkpoint_end_time,
            (SELECT COUNT(*) > i_limit FROM limited_checkpoints) AS has_more
        FROM
            runs.checkpoints C
                JOIN
            runs.measurements M ON C.id_checkpoint = M.fk_checkpoint
                JOIN
            runs.measure_definitions MD ON M.fk_measure_definition = MD.id_measure_definition
        WHERE
            C.id_checkpoint IN (SELECT LC.id_checkpoint FROM limited_checkpoints as LC WHERE LC.rn <= i_limit)
        ORDER BY
            C.process_start_time,
            C.id_checkpoint;

    IF NOT FOUND THEN
        status := 42;
        status_text := 'No checkpoint data found';
        RETURN NEXT;
    END IF;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

ALTER FUNCTION runs.get_partitioning_checkpoints(BIGINT, INT, BIGINT, TEXT) OWNER TO atum_owner;
GRANT EXECUTE ON FUNCTION runs.get_partitioning_checkpoints(BIGINT, INT, BIGINT, TEXT) TO atum_owner;
