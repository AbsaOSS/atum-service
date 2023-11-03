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


CREATE OR REPLACE FUNCTION runs._write_measurement(
    IN  i_fk_checkpoint     UUID,
    IN  i_fk_partitioning   BIGINT,
    IN  i_function_name     TEXT,
    IN  i_control_columns   TEXT[],
    IN  i_measurement_value JSONB,
    IN  i_by_user           TEXT,
    OUT status              INTEGER,
    OUT status_text         TEXT
) RETURNS record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs._write_measurement(6)
--      Adds the measure of the specified checkpoint. If the measure definition does not exists, it will be created.
--
-- Parameters:
--      i_fk_checkpoint         - reference to the checkpoint this measurement belongs into
--      i_fk_partitioning       - partitioning the measure belongs to
--      i_function_name         - type of the measure
--      i_control_columns       - set of fields the measure is applied on
--      i_measurement_value     - value of the measure
--      i_by_user               - user behind the change
--
-- Returns:
--      status              - Status code
--      status_text         - Status text
--
-- Status codes:
--      10                  - OK
--      11                  - Measurement added including measurement definition
--
-------------------------------------------------------------------------------
DECLARE
    _fk_checkpoint_measure_definition   BIGINT;
BEGIN
    SELECT CMD.id_checkpoint_measure_definition
    FROM runs.checkpoint_measure_definitions CMD
    WHERE CMD.fk_partitioning = i_fk_partitioning AND
          CMD.function_name = i_function_name AND
          CMD.control_columns = i_control_columns
    INTO _fk_checkpoint_measure_definition;

    IF NOT found THEN
        INSERT INTO runs.checkpoint_measure_definitions (fk_partitioning, function_name, control_columns, created_by)
        VALUES (i_fk_partitioning, i_function_name, i_control_columns, i_by_user)
        RETURNING id_checkpoint_measure_definition
        INTO _fk_checkpoint_measure_definition;

        status := 11;
        status_text := 'Measurement added including measurement definition';
    ELSE
        -- assuming ok result
        status := 10;
        status_text := 'OK';
    END IF;

    INSERT INTO runs.measurements (fk_checkpoint_measure_definition, fk_checkpoint, measurement_value)
    VALUES (_fk_checkpoint_measure_definition, i_fk_checkpoint, i_measurement_value);

    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

ALTER FUNCTION runs._write_measurement(UUID, BIGINT, TEXT, TEXT[], JSONB, TEXT) OWNER TO atum_owner;