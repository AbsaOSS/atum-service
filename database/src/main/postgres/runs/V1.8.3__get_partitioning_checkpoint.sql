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
    IN  i_fk_partitioning  BIGINT,
    OUT status             INTEGER,
    OUT status_text        TEXT,
    OUT id_checkpoint      UUID,
    OUT checkpoint_name    TEXT,
    OUT measure_name       TEXT,
    OUT measure_columns    TEXT[],
    OUT measurement_value  JSONB,
    OUT checkpoint_time    TIMESTAMP WITH TIME ZONE
)
    RETURNS SETOF record AS
$$
    -------------------------------------------------------------------------------
--
-- Function: runs.get_partitioning_checkpoints(JSONB)
--      Returns all the checkpoint for the given partitioning
--
-- Parameters:
--      i_partitioning      - partitioning for requested checkpoints
--
-- Returns:
--      status              - Status code
--      status_text         - Status message
--      id_checkpoint       - ID of the checkpoint
--      checkpoint_name     - Name of the checkpoint
--      measure_name        - Name of the measure
--      measure_columns     - Columns of the measure
--      measurement_value   - Value of the measurement
--      checkpoint_time     - Time of the checkpoint
--
-- Status codes:
--      11 - OK
--      16 - No record found for the given partitioning
--      41 - Partitioning not found
--
-------------------------------------------------------------------------------
DECLARE
    _fk_partitioning    BIGINT;
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
            c.process_start_time AS checkpoint_time
        FROM
            runs.checkpoints c
        JOIN
            runs.measurements m ON c.id_checkpoint = m.fk_checkpoint
        JOIN
            runs.measure_definitions md ON m.fk_measure_definition = md.id_measure_definition
        WHERE
                c.fk_partitioning = i_fk_partitioning
        ORDER BY
            c.process_start_time,
            c.id_checkpoint;

        IF NOT FOUND THEN
            status := 16;
            status_text := 'No checkpoints were found for the given partitioning.';
            RETURN NEXT;
            RETURN;
        END IF;
END;
$$

LANGUAGE plpgsql VOLATILE SECURITY DEFINER;
ALTER FUNCTION runs.get_partitioning_checkpoints(JSONB) OWNER TO atum_owner;

