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

CREATE OR REPLACE FUNCTION flow_patterns.get_flow(
    IN  i_flow_name         TEXT,
    OUT status              INTEGER,
    OUT status_text         TEXT,
    OUT id_fp_flow          BIGINT,
    OUT flow_name           TEXT,
    OUT flow_description    TEXT,
    OUT created_by          TEXT,
    OUT created_at        TIMESTAMP WITH TIME ZONE,
    OUT updated_by           TEXT,
    OUT updated_at         TIMESTAMP WITH TIME ZONE
) RETURNS record AS
$$
-------------------------------------------------------------------------------
--
-- Function: flow_patterns.get_flow(1)
--      Gets the information on the flow of the given name, if exists
--
-- Parameters:
--      i_flow_name         - Name of the flow to search for
--
-- Returns:
--      status              - Status code
--      status_text         - Status text
--      id_fp_flow          - id of the flow
--      flow_name           - name of the flow,
--      flow_description    - description of the flow
--      created_by          - user who created the flow
--      created_at          - when was the flow created
--      updated_by          - user who last edited the flow
--      updated_at          - when was the last edit of the flow
--
-- Status codes:
--      10                  - OK
--      40                  - Flow not found
--
-------------------------------------------------------------------------------
DECLARE
BEGIN
    SELECT F.id_fp_flow, F.flow_name, F.flow_description, F.created_by,
        F.created_at, F.updated_by, F.updated_at
    FROM flow_patterns.flows F
    WHERE F.flow_name = i_flow_name
    INTO id_fp_flow, flow_name, flow_description, created_by,
        created_at, updated_by, updated_at;

    IF found THEN
        status := 10;
        status_text := 'OK';
    ELSE
        status := 40;
        status_text := 'Flow not found';
    END IF;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION flow_patterns.get_flow(TEXT) TO atum_configurator;
