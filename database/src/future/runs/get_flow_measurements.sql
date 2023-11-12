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


CREATE OR REPLACE FUNCTION runs.get_flow_measurements(
    IN  i_id_flow           BIGINT,
    OUT status              INTEGER,
    OUT status_text         TEXT,
    OUT segmentations       HSTORE,
    OUT checkpoint_name     TEXT,
    OUT process_start_time  TIMESTAMP WITH TIME ZONE,
    OUT process_end_time    TIMESTAMP WITH TIME ZONE,
    OUT measure_type        TEXT,
    OUT measure_fields      TEXT[],
    OUT value               JSON
) RETURNS SETOF record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs.get_flow_measurements(1)
--      Gets all the measurements of the flow includinng segmentations and checkpoints they belong to.
--
-- Parameters:
--      i_id_flow           - id of the flow
--
-- Returns:
--      status              - Status code
--      status_text         - Status text
--      segmentations       - segmentation the measurement belongs into
--      checkpoint_name     - name of the checkpoint the measurement belongs into
--      process_start_time  - the time when the checkpoint measurements were started
--      process_end_time    - the time when the checkpoint measurements were finished
--      measure_type        - the type of the measurement
--      measure_fields      - fields the measurement is applied to
--      value               - value of the measurement
--
-- Status codes:
--      10                  - OK
--      40                  - Flow not found
--
-------------------------------------------------------------------------------
DECLARE
BEGIN
    PERFORM 1
    FROM runs.flows F
    WHERE F.id_flow = i_id_flow;

    IF NOT found THEN
        status := 40;
        status_text := 'Flow not found';
        RETURN NEXT;
        RETURN;
    END IF;

    RETURN QUERY
    SELECT 10, 'OK', S.segmentation,
           CP.checkpoint_name, CP.process_start_time, CP.process_end_time,
           CMD.measure_type, CMD.measure_fields, M.value
    FROM runs.segmentation_to_flow STF INNER JOIN
        runs.segmentations S ON S.id_segmentation = STF.key_segmentation INNER JOIN
        runs.checkpoints CP ON CP.key_segmentation = STF.key_segmentation INNER JOIN
        runs.measurements M ON M.key_checkpoint = CP.id_checkpoint INNER JOIN
        runs.measure_definitions CMD ON CMD.id_measure_definition = M.key_measure_definition
    WHERE STF.key_flow = i_id_flow;

    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION runs.get_flow_measurements(BIGINT) TO atum_user;
