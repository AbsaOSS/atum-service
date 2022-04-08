/*
 * Copyright 2021 ABSA Group Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

CREATE OR REPLACE FUNCTION runs.get_segmentation_measurements(
    IN  i_segmentation                               HSTORE,
    OUT status                                       INTEGER,
    OUT status_text                                  TEXT,
    OUT id_measurement                               UUID,
    OUT id_checkpoint_measure_definition             BIGINT,
    OUT id_checkpoint                                UUID,
    OUT value                                        JSON,
    OUT checkpoint_name                              TEXT,
    OUT workflow_name                                TEXT,
    OUT checkpoint_process_start_time                TIMESTAMP WITH TIME ZONE,
    OUT checkpoint_process_end_time                  TIMESTAMP WITH TIME ZONE,
    OUT checkpoint_created_by                        TEXT,
    OUT checkpoint_created_at                        TIMESTAMP WITH TIME ZONE,
    OUT measure_type                                 TEXT,
    OUT measure_fields                               TEXT[],
    OUT checkpoint_measure_definition_created_by     TEXT,
    OUT checkpoint_measure_definition_created_at     TIMESTAMP WITH TIME ZONE
) RETURNS SETOF record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs.get_segmentation_measurements(1)
--      Returns all measurements (together with checkpoints and checkpoint measure definitions info) that are associated with the given segmentation.
--      If the given segmentation does not exist, exactly one record is returned with the error status.
--
-- Parameters:
--      i_segmentation                                      - segmentation for which the data are bound to
--
-- Returns:
--      status                                              - Status code
--      status_text                                         - Status text
--      id_measurement                                      - Measurement id
--      id_checkpoint_measure_definition                    - Checkpoint measure definition id
--      id_checkpoint                                       - Checkpoint id
--      value                                               - Measurement value
--      checkpoint_name                                     - Checkpoint name
--      workflow_name                                       - Workflow name
--      checkpoint_process_start_time                       - Checkpoint process start time
--      checkpoint_process_end_time                         - Checkpoint process end time
--      checkpoint_created_by                               - Checkpoint created by
--      checkpoint_created_at                               - Checkpoint created at
--      measure_type                                        - Measure type
--      measure_fields                                      - Measure fields
--      checkpoint_measure_definition_created_by            - Checkpoint measure definition created by
--      checkpoint_measure_definition_created_at            - Checkpoint measure definition created at
--
-- Status codes:
--      10                                                  - OK
--      41                                                  - Segmentation not found
--
-------------------------------------------------------------------------------
DECLARE
    _key_segmentation   BIGINT;
BEGIN
    _key_segmentation = runs._get_key_segmentation(i_segmentation);

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
      m.key_checkpoint_measure_definition AS id_checkpoint_measure_definition,
      m.key_checkpoint AS id_checkpoint,
      m.value,
      ch.checkpoint_name,
      ch.workflow_name,
      ch.process_start_time AS checkpoint_process_start_time,
      ch.process_end_time AS checkpoint_process_end_time,
      ch.created_by AS checkpoint_created_by,
      ch.created_at AS checkpoint_created_at,
      chmd.measure_type,
      chmd.measure_fields,
      chmd.created_by AS checkpoint_measure_definition_created_by,
      chmd.created_at AS checkpoint_measure_definition_created_at
    FROM runs.checkpoints ch
    INNER JOIN runs.measurements m
      ON ch.id_checkpoint = m.key_checkpoint
    INNER JOIN runs.checkpoints_measure_definitions chmd
      ON m.key_checkpoint_measure_definition = chmd.id_checkpoint_measure_definition
    WHERE ch.key_segmentation = _key_segmentation;

END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION runs.get_segmentation_measurements(HSTORE) TO atum_user;