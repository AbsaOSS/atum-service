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


CREATE OR REPLACE FUNCTION runs.write_checkpoint(
    IN  i_partitioning          JSONB,
    IN  i_id_checkpoint         UUID,
    IN  i_checkpoint_name       TEXT,
    IN  i_process_start_time    TIMESTAMP WITH TIME ZONE,
    IN  i_process_end_time      TIMESTAMP WITH TIME ZONE,
    IN  i_function_names        TEXT[],
    IN  i_control_columns       TEXT[][],
    IN  i_measure_values        JSONB[],
    IN  i_by_user               TEXT,
    OUT status                  INTEGER,
    OUT status_text             TEXT
) RETURNS record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs.write_measurement(9)
--      Creates a checkpoint and adds all the measurements that it consists of
--
-- Parameters:
--      i_partitioning          - segmentation the measure belongs to
--      i_id_checkpoint         - reference to the checkpoint this measure belongs into
--      i_checkpoint_name       - name of the checkpoint
--      i_process_start_time    - the start of processing (measuring) of the checkpoint
--      i_i_process_end_time    - the end of the processing (measuring) of the checkpoint
--      i_function_names        - functions used for measurements
--      i_control_columns       - set of field set the measures are applied on
--      i_measure_values        - values of the measure
--      i_by_user               - user behind the change
--
-- Returns:
--      status              - Status code
--      status_text         - Status text
--
-- Status codes:
--      11                  - Checkpoint created
--      14                  - Checkpoint already present
--      41                  - Partitioning not found
--
-------------------------------------------------------------------------------
DECLARE
    _fk_partitioning                    BIGINT;
BEGIN

    PERFORM 1
    FROM runs.checkpoints CP
    WHERE CP.id_checkpoint = i_id_checkpoint;

    IF found THEN
        status := 14;
        status_text := 'Checkpoint already present';
        RETURN;
    END IF;

    _fk_partitioning = runs._get_id_partitioning(i_partitioning);

    IF _fk_partitioning IS NULL THEN
        status := 41;
        status_text := 'Partitioning not found';
        RETURN;
    END IF;

    INSERT INTO runs.checkpoints (id_checkpoint, fk_partitioning, checkpoint_name, process_start_time, process_end_time, created_by)
    VALUES (i_id_checkpoint, _fk_partitioning, i_checkpoint_name, i_process_start_time, i_process_end_time, i_by_user);

    PERFORM runs._write_measurement(
        i_id_checkpoint,
        _fk_partitioning,
        function_name,
        control_columns,
        measure_value,
        i_by_user
        )
    FROM (
        SELECT
            unnest(i_function_names) AS function_name,
            unnest(i_control_columns) AS control_columns,
            unnest(i_measure_values) AS measure_value
        ) UN;

    status := 11;
    status_text := 'Checkpoint created';
    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

ALTER FUNCTION runs.write_checkpoint(JSONB, UUID, TEXT, TIMESTAMP WITH TIME ZONE, TIMESTAMP WITH TIME ZONE, TEXT[], TEXT[][], JSONB[], TEXT) OWNER TO atum_owner;
GRANT EXECUTE ON FUNCTION runs.write_checkpoint(JSONB, UUID, TEXT, TIMESTAMP WITH TIME ZONE, TIMESTAMP WITH TIME ZONE, TEXT[], TEXT[][], JSONB[], TEXT) TO atum_user;
