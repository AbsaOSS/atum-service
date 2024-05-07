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

-- Function: runs.get_partitioning_checkpoints(JSONB, INT, TEXT)
CREATE OR REPLACE FUNCTION runs.get_partitioning_checkpoints(
    IN i_partitioning              JSONB,
    IN i_limit                     INT DEFAULT 5,
    IN i_checkpoint_name           TEXT DEFAULT NULL,
    OUT status                     INTEGER,
    OUT status_text                TEXT,
    OUT id_checkpoint              UUID,
    OUT checkpoint_name            TEXT,
    OUT measure_name               TEXT,
    OUT measure_columns            TEXT[],
    OUT measurement_value          JSONB,
    OUT checkpoint_start_time      TIMESTAMP WITH TIME ZONE,
    OUT checkpoint_end_time        TIMESTAMP WITH TIME ZONE
)
    RETURNS SETOF record AS
$$
    -------------------------------------------------------------------------------
--
-- Function: runs.get_partitioning_checkpoints(JSONB, INT, TEXT)
--      Returns all the checkpoint for the given partitioning and checkpoint name
--
-- Parameters:
--      i_partitioning      - partitioning of requested checkpoints
--      i_limit                 - (optional) maximum number of data returned
--                                if 0 specified, all data will be returned, i.e. no limit will be applied
--      i_checkpoint_name       - (optional) if specified, returns data related to particular checkpoint's name
--
-- Returns:
--      status                  - Status code
--      status_text             - Status message
--      id_checkpoint           - ID of the checkpoint
--      checkpoint_name         - Name of the checkpoint
--      measure_name            - Name of the measure
--      measure_columns         - Columns of the measure
--      measurement_value       - Value of the measurement
--      checkpoint_start_time   - Time of the checkpoint
--      checkpoint_end_time     - End time of the checkpoint computation
--
-- Status codes:
--      11 - OK
--      41 - Partitioning not found
--
-------------------------------------------------------------------------------
DECLARE
    _fk_partitioning                    BIGINT;
BEGIN
    _fk_partitioning = runs._get_id_partitioning(i_partitioning);

    IF _fk_partitioning IS NULL THEN
        status := 41;
        status_text := 'Partitioning not found';
        RETURN NEXT;
        RETURN;
    END IF;

    RETURN QUERY
        SELECT
            11 AS status,
            'Ok' AS status_text,
            c.id_checkpoint,
            c.checkpoint_name,
            md.measure_name,
            md.measured_columns,
            m.measurement_value,
            c.process_start_time AS checkpoint_start_time,
            c.process_end_time AS checkpoint_end_time
        FROM
            runs.checkpoints c
        JOIN
            runs.measurements m ON c.id_checkpoint = m.fk_checkpoint
        JOIN
            runs.measure_definitions md ON m.fk_measure_definition = md.id_measure_definition
        WHERE
            c.fk_partitioning = _fk_partitioning
        AND
            (i_checkpoint_name IS NULL OR c.checkpoint_name = i_checkpoint_name)
        ORDER BY
            c.process_start_time,
            c.id_checkpoint
        LIMIT nullif(i_limit, 0);

END;
$$

LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

ALTER FUNCTION runs.get_partitioning_checkpoints(JSONB, INT, TEXT) OWNER TO atum_owner;

GRANT EXECUTE ON FUNCTION runs.get_partitioning_checkpoints(JSONB, INT, TEXT) TO atum_owner;

