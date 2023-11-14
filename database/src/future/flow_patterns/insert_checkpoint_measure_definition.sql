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

CREATE OR REPLACE FUNCTION flow_patterns.set_measure_definition(
    IN  i_flow_name                         TEXT,
    IN  i_measure_type                      TEXT,
    IN  i_measure_fields                    TEXT[],
    IN  i_by_user                           TEXT,
    OUT status                              INTEGER,
    OUT status_text                         TEXT,
    OUT id_fp_measure_definition BIGINT,
    OUT key_fp_flow                         BIGINT
) RETURNS record AS
$$
-------------------------------------------------------------------------------
--
-- Function: flow_patterns.set_measure_definition(4)
--      Adds a measure definition pattern to the given flow.
--
-- Parameters:
--      i_flow_name         - flow name the entry belongs to
--      i_measure_type      - the measure type
--      i_measure_fields    - the fields the measure is to be applied to
--      i_by_user           - user initiating the change
--
-- Returns:
--      status                              - Status code
--      status_text                         - Status text
--      id_fp_measure_definition - id of the measure definition entry
--      key_fp_flow                         - id of the flow the additional data pattern were added into
--
-- Status codes:
--      11                  - Measure definition entry created
--      41                  - Flow not found
--
-------------------------------------------------------------------------------
DECLARE
    _id_fp_measure_definition    BIGINT;
BEGIN
    SELECT GF.status, GF.status_text, GF.id_fp_flow
    FROM flow_patterns.get_flow(i_flow_name) GF
    INTO status, status_text, key_fp_flow;

    IF status = 10 THEN

        INSERT INTO flow_patterns.measure_definitions(key_fp_flow, measure_type, measure_fields, created_by, updated_by)
        VALUES (key_fp_flow, i_measure_type, i_measure_fields, i_by_user, i_by_user)
        RETURNING flow_patterns.measure_definitions.id_fp_measure_definition
        INTO id_fp_measure_definition;

        status := 11;
        status_text := 'Measure definition entry created';
    ELSE
        -- flow not found, status_text can stay
        status := 41;
    END IF;

    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION flow_patterns.set_measure_definition(TEXT, TEXT, TEXT[], TEXT) TO atum_configurator;
