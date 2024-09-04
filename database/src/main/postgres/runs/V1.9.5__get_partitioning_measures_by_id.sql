/*
 * Copyright 2021 ABSA Group Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

-- Function: runs.get_partitioning_measures_by_id(Long)
CREATE OR REPLACE FUNCTION runs.get_partitioning_measures_by_id(
    IN i_partitioning_id          BIGINT,
    OUT status                    INTEGER,
    OUT status_text               TEXT,
    OUT measure_name              TEXT,
    OUT measured_columns          TEXT[]
) RETURNS SETOF record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs.get_partitioning_measures_by_id(1)
--      Returns measures for the given partitioning id
--
-- Parameters:
--      i_partitioning_id       - partitioning id we are asking the measures for
--
-- Returns:
--      status                  - Status code
--      status_text             - Status message
--      measure_name            - Name of the measure
--      measured_columns        - Array of columns associated with the measure
--
-- Status codes:
--      11 - OK
--      41 - Partitioning not found
--
-------------------------------------------------------------------------------
BEGIN

    PERFORM 1 FROM runs.partitionings WHERE id_partitioning = i_partitioning_id;

    IF NOT FOUND THEN
        status := 41;
        status_text := 'Partitioning not found';
        RETURN NEXT;
        RETURN;
    END IF;

    RETURN QUERY
        SELECT 11, 'OK', MD.measure_name, MD.measured_columns
        FROM runs.measure_definitions AS MD
        WHERE MD.fk_partitioning = i_partitioning_id;

    RETURN;

END;
$$
LANGUAGE plpgsql volatile SECURITY DEFINER;

ALTER FUNCTION runs.get_partitioning_measures_by_id(BIGINT) OWNER TO atum_owner;
GRANT EXECUTE ON FUNCTION runs.get_partitioning_measures_by_id(BIGINT) TO atum_user;
