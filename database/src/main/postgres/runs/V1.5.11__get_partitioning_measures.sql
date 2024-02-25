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


 CREATE OR REPLACE FUNCTION runs.get_partitioning_measures(
     IN i_partitioning          JSONB,
     OUT measure_name           TEXT,
     OUT measured_columns       TEXT[],
     OUT status                 INTEGER,
     OUT status_text            TEXT
 ) RETURNS SETOF record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs.get_partitioning_measures(2)
--      Iterates over a JSONB object and returns each key-value pair as a record
--
-- Parameters:
--      i_partitioning      - JSONB object where each key is a measure name and its corresponding value is an array of measured columns
--
-- Returns:
--      measure_name        - Name of the measure
--      measured_columns    - Array of columns associated with the measure
--      status              - Status code
--      status_text         - Status message

-- Status codes:
--      10 - Record not found for the given partitioning
--      11 - OK
--      41 - Partitioning not found
--
-------------------------------------------------------------------------------

DECLARE
    _fk_partitioning                    BIGINT;
    _measure_name                       TEXT;
    _measured_columns                   TEXT[];
BEGIN
    _fk_partitioning = runs._get_id_partitioning(i_partitioning);

    IF _fk_partitioning IS NULL THEN
        measure_name := NULL;
        measured_columns := NULL;
        status := 41;
        status_text := 'The partitioning does not exist.';
        RETURN NEXT;
    END IF;

    SELECT md.measure_name, md.measured_columns
    INTO _measure_name, _measured_columns
    FROM runs.measure_definitions AS md
    WHERE md.fk_partitioning = _fk_partitioning;

    IF NOT FOUND THEN
        measure_name := NULL;
        measured_columns := NULL;
        status := 10;
        status_text := 'No measures found for the given partitioning.';
    ELSE
        RETURN NEXT;
    END IF;
    RETURN NEXT;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

ALTER FUNCTION runs.get_partitioning_measures(JSONB) OWNER TO atum_owner;
