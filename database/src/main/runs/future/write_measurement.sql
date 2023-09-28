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


CREATE OR REPLACE FUNCTION runs.write_measurement(
    IN  i_segmentation          JSONB,
    IN  i_id_checkpoint         UUID,
    IN  i_measure_type          TEXT[],
    IN  i_measure_fields        TEXT[][],
    IN  i_value                 JSONB[],
    IN  i_by_user               TEXT,
    OUT status              INTEGER,
    OUT status_text         TEXT
) RETURNS record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs.write_measurement(7)
--      Adds the measure of the specified checkpoint. If the measure definition does not exists, it will be created.
--
-- Parameters:
--      i_segmentation          - segmentation the measure belongs to
--      i_id_measurement        - unique identifier of the measure to ensure idempotence
--      i_id_checkpoint         - reference to the checkpoint this measure belongs into
--      i_measure_type          - type of the measure
--      i_measure_fields        - set of fields the measure is applied on
--      i_value                 - value of the measure
--      i_by_user               - user behind the change
--
-- Returns:
--      status              - Status code
--      status_text         - Status text
--
-- Status codes:
--      10                  - OK
--      11                  - Measurement added including measurement defintion'
--      14                  - Measurement already present
--      31                  - Checkpoint is closed, measurements cannot be added anymore
--      41                  - Segmentation not found
--      43                  - Checkpoint not found
--
-------------------------------------------------------------------------------
DECLARE
    _key_segmentation                   BIGINT;
    _key_checkpoint_measure_definition  BIGINT;
    _checkpoint_closed                  BOOLEAN;
BEGIN

    PERFORM 1
    FROM runs.measurements M
    WHERE M.id_measurement = i_id_measurement;

    IF found THEN
        status := 14;
        status_text := 'Measurement already present';
        RETURN;
    END IF;

    _key_segmentation = runs._get_key_segmentation(i_segmentation);

    IF _key_segmentation IS NULL THEN
        status := 41;
        status_text := 'Segmentation not found';
        RETURN;
    END IF;

    SELECT CP.process_end_time IS NOT NULL
    FROM runs.checkpoints CP
    WHERE CP.id_checkpoint = i_id_checkpoint AND
          CP.key_segmentation = _key_segmentation
    INTO _checkpoint_closed;

    IF NOT found THEN
        status := 43;
        status_text := 'Checkpoint not found';
        RETURN;
    END IF;

    IF _checkpoint_closed THEN
        status := 31;
        status_text := 'Checkpoint is closed, measurements cannot be added anymore';
        RETURN;
    END IF;

    SELECT CMD.id_checkpoint_measure_definition
    FROM runs.checkpoint_measure_definitions CMD
    WHERE CMD.key_segmentation = _key_segmentation AND
          CMD.measure_type = i_measure_type AND
          CMD.measure_fields = i_measure_fields
    INTO _key_checkpoint_measure_definition;

    IF NOT found THEN
        INSERT INTO runs.checkpoint_measure_definitions (key_segmentation, measure_type, measure_fields, created_by)
        VALUES (_key_segmentation, i_measure_type, i_measure_fields, i_by_user)
        RETURNING id_checkpoint_measure_definition
        INTO _key_checkpoint_measure_definition;

        status := 11;
        status_text := 'Measurement added including measurement defintion';
    ELSE
        -- assuming ok result
        status := 10;
        status_text := 'OK';
    END IF;

    INSERT INTO runs.measurements (id_measurement, key_checkpoint_measure_definition, key_checkpoint, value)
    VALUES (i_id_measurement, _key_checkpoint_measure_definition, i_id_checkpoint, i_value);

    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION runs.write_measurement(JSONB, UUID, TEXT, TEXT[], JSON, TEXT) TO atum_user;
