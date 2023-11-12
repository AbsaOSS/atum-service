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

CREATE OR REPLACE FUNCTION flow_patterns.get_flow_measure_definitions(
    IN  i_flow_name                         TEXT,
    OUT status                              INTEGER,
    OUT status_text                         TEXT,
    OUT id_fp_measure_definition BIGINT,
    OUT measure_type                        TEXT,
    OUT measure_fields                      TEXT[],
    OUT created_by                          TEXT,
    OUT created_at                          TIMESTAMP WITH TIME ZONE,
    OUT updated_by                          TEXT,
    OUT updated_at                          TIMESTAMP WITH TIME ZONE
) RETURNS SETOF record AS
$$
-------------------------------------------------------------------------------
--
-- Function: flow_patterns.get_flow_measure_definitions(1)
--      Gets all the control measure definitions of the flow.
--      If flow of the given name does not exists one record with status 40 is returned.
--
-- Parameters:
--      i_flow_name             - Name of the flow which additional data patterns to get         -
--
-- Returns:
--      status                              - Status code
--      status_text                         - Status text
--      id_fp_measure_definition - Id of the additional data entry
--      measure_type                        - type of the control measure
--      measure_fields                      - dlist of the fields the measure is applied to
--      created_by                          - user who created the entry
--      created_when                        - when was the entry created
--      edited_by                           - user who last edited the entry
--      edited_when                         - when was the last edit of the entry
--
-- Status codes:
--      10                  - OK
--      40                  - Flow not found
--
-------------------------------------------------------------------------------
DECLARE
    _key_fp_flow     BIGINT;
BEGIN
    SELECT GF.status, GF.status_text, GF.id_fp_flow
    FROM flow_patterns.get_flow(i_flow_name) GF
    INTO status, status_text, _key_fp_flow;

    IF status = 10 THEN
        RETURN QUERY
            SELECT 10,'OK',CMD.id_fp_measure_definition,CMD.measure_type,
                   CMD.measure_fields,CMD.created_by,CMD.created_at,CMD.updated_by,
                   CMD.updated_at
            FROM flow_patterns.measure_definitions CMD
            WHERE CMD.key_fp_flow = _key_fp_flow;
    ELSE
        RETURN NEXT;
    END IF;

    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION flow_patterns.get_flow_measure_definitions(TEXT) TO atum_configurator;
