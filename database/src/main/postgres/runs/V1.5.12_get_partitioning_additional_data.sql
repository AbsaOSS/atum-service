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

CREATE OR REPLACE FUNCTION runs.get_partitioning_additional_data(
    IN  i_partitioning          JSONB,
    OUT ad_name                 TEXT,
    OUT ad_value                TEXT,
    OUT status                  INTEGER,
    OUT status_text             TEXT
) RETURNS SETOF record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs.get_partitioning_additional_data(2)
--      Iterates over a JSONB object and returns each key-value pair as a record
--
-- Parameters:
--      i_partitioning      - JSONB object where each key is an additional data name and its corresponding value is the additional data value
--
-- Returns:
--      ad_name             - Name of the additional data
--      ad_value            - Value of the additional data
--
-------------------------------------------------------------------------------

DECLARE
    _fk_partitioning                    BIGINT;
BEGIN
    _fk_partitioning = runs._get_id_partitioning(i_partitioning);

    IF _fk_partitioning IS NULL THEN
        ad_name := 'Partitioning not found';
        ad_value := '';
        status := 41;
        status_text := 'The partitioning does not exist.';
        RETURN;
    END IF;

    status = 11;
    status_text = 'OK';

    RETURN QUERY
        SELECT mdh.ad_name, mdh.ad_value, status, status_text
        FROM runs.measure_definitions_history AS mdh
        WHERE mdh.fk_partitioning = _fk_partitioning;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

ALTER FUNCTION runs.get_partitioning_additional_data(JSONB, BOOLEAN) OWNER TO atum_owner;
