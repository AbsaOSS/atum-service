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

CREATE OR REPLACE FUNCTION runs.get_partitioning_measurements(
    IN  i_partitioning                               JSONB,
    OUT status                                       INTEGER,
    OUT status_text                                  TEXT,
    OUT id_measurement                               UUID,
    OUT checkpoint_name                              TEXT,
    OUT workflow_name                                TEXT,
    OUT checkpoint_process_start_time                TIMESTAMP WITH TIME ZONE,
    OUT checkpoint_process_end_time                  TIMESTAMP WITH TIME ZONE,
    OUT measure_type                                 TEXT,
    OUT measure_fields                               TEXT[],
    OUT value                                        JSONB
) RETURNS SETOF record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs.get_partitioning_measurements(1)
--      Returns all measurements (together with checkpoints and checkpoint measure definitions info) that are associated with the given segmentation.
--      If the given segmentation does not exist, exactly one record is returned with the error status.
--
-- Parameters:
--      i_partitioning                                      - segmentation for which the data are bound to
--
-- Returns:
--      status                                              - Status code
--      status_text                                         - Status text
--      id_measurement                                      - Measurement id
--      checkpoint_name                                     - Checkpoint name
--      workflow_name                                       - Workflow name
--      checkpoint_process_start_time                       - Checkpoint process start time
--      checkpoint_process_end_time                         - Checkpoint process end time
--      measure_type                                        - Measure type
--      measure_fields                                      - Measure fields
--      value                                               - Measurement value
--
-- Status codes:
--      10                                                  - OK
--      41                                                  - Segmentation not found
--
-------------------------------------------------------------------------------
DECLARE
    _key_segmentation   BIGINT;
BEGIN
    _key_segmentation = runs._get_key_segmentation(i_partitioning);

    IF _key_segmentation IS NULL THEN
        status := 41;
        status_text := 'Segmentation not found';
        RETURN NEXT;
        RETURN;
    END IF;

    RETURN QUERY
    SELECT
      10,
      'OK',
      m.id_measurement,
      cp.checkpoint_name,
      cp.workflow_name,
      cp.process_start_time AS checkpoint_process_start_time,
      cp.process_end_time AS checkpoint_process_end_time,
      cpmd.measure_type,
      cpmd.measure_fields,
      m.value
    FROM runs.checkpoints cp
    INNER JOIN runs.measurements m
      ON cp.id_checkpoint = m.key_checkpoint
    INNER JOIN runs.checkpoints_measure_definitions cpmd
      ON m.key_measure_definition = cpmd.id_measure_definition
    WHERE cp.key_segmentation = _key_segmentation;

    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

ALTER FUNCTION runs.get_partitioning_measurements(JSONB) OWNER TO atum_owner;
GRANT EXECUTE ON FUNCTION runs.get_partitioning_measurements(JSONB) TO atum_user;
