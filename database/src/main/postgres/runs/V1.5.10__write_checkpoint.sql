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
    IN  i_partitioning              JSONB,
    IN  i_id_checkpoint             UUID,
    IN  i_checkpoint_name           TEXT,
    IN  i_process_start_time        TIMESTAMP WITH TIME ZONE,
    IN  i_process_end_time          TIMESTAMP WITH TIME ZONE,
    IN  i_measurements              JSONB[],
    in  i_measured_by_atum_agent    BOOLEAN,
    IN  i_by_user                   TEXT,
    OUT status                      INTEGER,
    OUT status_text                 TEXT
) RETURNS record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs.write_checkpoint(10)
--      Creates a checkpoint and adds all the measurements that it consists of
--
-- Parameters:
--      i_partitioning              - partitioning the measure belongs to
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
--      31                  - Checkpoint already present
--      41                  - Partitioning not found
--
-------------------------------------------------------------------------------
DECLARE
    _fk_partitioning                    BIGINT;
BEGIN

    _fk_partitioning = runs._get_id_partitioning(i_partitioning);

    IF _fk_partitioning IS NULL THEN
        status := 41;
        status_text := 'Partitioning not found';
        RETURN;
    END IF;

    SELECT WC.status, WC.status_text
    FROM runs.write_checkpoint(
                 _fk_partitioning,
                 i_id_checkpoint,
                 i_checkpoint_name,
                 i_process_start_time,
                 i_process_end_time,
                 i_measurements,
                 i_measured_by_atum_agent,
                 i_by_user
         ) WC
    INTO status, status_text;

    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

ALTER FUNCTION runs.write_checkpoint(JSONB, UUID, TEXT, TIMESTAMP WITH TIME ZONE, TIMESTAMP WITH TIME ZONE, JSONB[], BOOLEAN, TEXT) OWNER TO atum_owner;
GRANT EXECUTE ON FUNCTION runs.write_checkpoint(JSONB, UUID, TEXT, TIMESTAMP WITH TIME ZONE, TIMESTAMP WITH TIME ZONE, JSONB[], BOOLEAN, TEXT) TO atum_user;
