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

CREATE OR REPLACE FUNCTION runs.get_partitioning_checkpoint_v2(
    IN i_partitioning_id           BIGINT,
    IN i_checkpoint_id             UUID,
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
    OUT checkpoint_end_time        TIMESTAMP WITH TIME ZONE
)
    RETURNS SETOF record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs.get_partitioning_checkpoint_v2(BIGINT, UUID)
--      Retrieves a single checkpoint (measures and their measurement details) related to a
--      given partitioning and checkpoint ID.
--
-- Parameters:
--      i_partitioning_id        - ID of the partitioning
--      i_checkpoint_id          - ID of the checkpoint
--
-- Returns:
--      status                  - Status code
--      status_text             - Status message
--      id_checkpoint           - ID of the checkpoint
--      checkpoint_name         - Name of the checkpoint
--      author                  - Author of the checkpoint
--      measuredByAtumAgent     - Flag indicating whether the checkpoint was measured by ATUM agent
--      measure_name            - Name of the measure
--      measure_columns         - Columns of the measure
--      measurement_value       - Value of the measurement
--      checkpoint_start_time   - Time of the checkpoint
--      checkpoint_end_time     - End time of the checkpoint computation
--
-- Status codes:
--      11 - OK
--      41 - Partitioning not found
--      42 - Checkpoint not found
--
-------------------------------------------------------------------------------
BEGIN
    PERFORM 1 FROM runs.partitionings WHERE id_partitioning = i_partitioning_id;
    IF NOT FOUND THEN
        status := 41;
        status_text := 'Partitioning not found';
        RETURN NEXT;
        RETURN;
    END IF;

    RETURN QUERY
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
            C.process_end_time AS checkpoint_end_time
        FROM
            runs.checkpoints C
                JOIN
            runs.measurements M ON C.id_checkpoint = M.fk_checkpoint
                JOIN
            runs.measure_definitions MD ON M.fk_measure_definition = MD.id_measure_definition
        WHERE
            C.fk_partitioning = i_partitioning_id
          AND
            C.id_checkpoint = i_checkpoint_id;

    IF NOT FOUND THEN
        status := 42;
        status_text := 'Checkpoint not found';
        RETURN NEXT;
        RETURN;
    END IF;
END;
$$

LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

ALTER FUNCTION runs.get_partitioning_checkpoint_v2(BIGINT, UUID) OWNER TO atum_owner;

GRANT EXECUTE ON FUNCTION runs.get_partitioning_checkpoint_v2(BIGINT, UUID) TO atum_owner;
