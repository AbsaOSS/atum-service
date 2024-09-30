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
     OUT status                 INTEGER,
     OUT status_text            TEXT,
     OUT measure_name           TEXT,
     OUT measured_columns       TEXT[]
 ) RETURNS SETOF record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs.get_partitioning_measures(1)
--      Returns measures for the given partitioning
--
-- Parameters:
--      i_partitioning      - partitioning we are asking the measures for
--
-- Returns:
--      status              - Status code
--      status_text         - Status message
--      measure_name        - Name of the measure
--      measured_columns    - Array of columns associated with the measure

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

    status := 11;
    status_text := 'OK';

    RETURN QUERY
    SELECT status, status_text, md.measure_name, md.measured_columns
    FROM runs.measure_definitions AS md
    WHERE md.fk_partitioning = _fk_partitioning;

END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

ALTER FUNCTION runs.get_partitioning_measures(JSONB) OWNER TO atum_owner;
GRANT EXECUTE ON FUNCTION runs.get_partitioning_measures(JSONB) TO atum_user;
