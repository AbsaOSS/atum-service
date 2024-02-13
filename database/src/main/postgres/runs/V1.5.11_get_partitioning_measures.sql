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

 CREATE OR REPLACE runs.get_partitioning_measures(
     IN i_partitioning JSONB,
     OUT measure_name TEXT,
     OUT measure_column TEXT[]
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
--
-------------------------------------------------------------------------------

DECLARE
key text;
BEGIN
    FOR key IN SELECT jsonb_object_keys(i_partitioning)
    LOOP
        measure_name := key;
        measured_columns := ARRAY(SELECT jsonb_array_elements_text(i_partitioning -> key));
        RETURN NEXT;
    END LOOP;

    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

ALTER FUNCTION runs.get_partitioning_measures(JSONB) OWNER TO atum_owner;
