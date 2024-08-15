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

CREATE OR REPLACE FUNCTION runs.write_checkpoint_v2(
    IN  i_partitioning_id           BIGINT,
    IN  i_id_checkpoint             UUID,
    IN  i_checkpoint_name           TEXT,
    IN  i_process_start_time        TIMESTAMP WITH TIME ZONE,
    IN  i_process_end_time          TIMESTAMP WITH TIME ZONE,
    IN  i_measurements              JSONB[],
    IN  i_measured_by_atum_agent    BOOLEAN,
    IN  i_by_user                   TEXT,
    OUT status                      INTEGER,
    OUT status_text                 TEXT
) RETURNS record AS
$$
    -------------------------------------------------------------------------------
--
-- Function: runs.write_checkpoint_v2(8)
--      Creates a checkpoint and adds all the measurements that it consists of
--
-- Parameters:
--      i_partitioning_id           - ID of the partitioning the measure belongs to
--      i_id_checkpoint             - reference to the checkpoint this measure belongs into
--      i_checkpoint_name           - name of the checkpoint
--      i_process_start_time        - the start of processing (measuring) of the checkpoint
--      i_process_end_time          - the end of the processing (measuring) of the checkpoint
--      i_measurements              - array of JSON objects of the following format (values of the keys are examples only)
--                                    {
--                                      "measure": {
--                                        "measureName": "count",
--                                        "measuredColumns": ["a","b"]
--                                      },
--                                      "result": {
--                                        whatever here
--                                      }
--                                    }
--      i_measured_by_atum_agent    - flag it the checkpoint was measured by Atum or data provided by user
--      i_by_user                   - user behind the change
--
-- Returns:
--      status              - Status code
--      status_text         - Status text
--
-- Status codes:
--      11                  - Checkpoint created
--      31                  - Conflict, checkpoint already present
--      32                  - Partitioning not found
--
-------------------------------------------------------------------------------
BEGIN
    -- Check if partitioning exists
    IF NOT EXISTS (SELECT 1 FROM runs.partitionings WHERE id_partitioning = i_partitioning_id) THEN
        status := 32;
        status_text := 'Partitioning not found';
        RETURN;
    END IF;

    PERFORM 1
    FROM runs.checkpoints CP
    WHERE CP.id_checkpoint = i_id_checkpoint;

    IF found THEN
        status := 31;
        status_text := 'Checkpoint already present';
        RETURN;
    END IF;

    INSERT INTO runs.checkpoints (id_checkpoint, fk_partitioning,
                                  checkpoint_name, measured_by_atum_agent,
                                  process_start_time, process_end_time, created_by)
    VALUES (i_id_checkpoint, i_partitioning_id,
            i_checkpoint_name, i_measured_by_atum_agent,
            i_process_start_time, i_process_end_time, i_by_user);

    -- maybe could use `jsonb_populate_record` function to be little bit more effective
    PERFORM runs._write_measurement(
            i_id_checkpoint,
            i_partitioning_id,
            UN.measurement->'measure'->>'measureName',
            jsonb_array_to_text_array(UN.measurement->'measure'->'measuredColumns'),
            UN.measurement->'result',
            i_by_user
            )
    FROM (
             SELECT unnest(i_measurements) AS measurement
         ) UN;

    status := 11;
    status_text := 'Checkpoint created';
    RETURN;
END;
$$
    LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

ALTER FUNCTION runs.write_checkpoint_v2(BIGINT, UUID, TEXT, TIMESTAMP WITH TIME ZONE, TIMESTAMP WITH TIME ZONE, JSONB[], BOOLEAN, TEXT) OWNER TO atum_owner;
GRANT EXECUTE ON FUNCTION runs.write_checkpoint_v2(BIGINT, UUID, TEXT, TIMESTAMP WITH TIME ZONE, TIMESTAMP WITH TIME ZONE, JSONB[], BOOLEAN, TEXT) TO atum_user;
