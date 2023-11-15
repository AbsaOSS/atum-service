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

CREATE OR REPLACE FUNCTION flow_patterns.get_flow_additional_data(
    IN  i_flow_name             TEXT,
    OUT status                  INTEGER,
    OUT status_text             TEXT,
    OUT id_fp_additional_data   BIGINT,
    OUT ad_name                 TEXT,
    OUT ad_default_value        TEXT,
    OUT created_by              TEXT,
    OUT created_at              TIMESTAMP WITH TIME ZONE,
    OUT updated_by              TEXT,
    OUT updated_at              TIMESTAMP WITH TIME ZONE
) RETURNS SETOF record AS
$$
-------------------------------------------------------------------------------
--
-- Function: flow_patterns.get_flow_additional_data(1)
--      Gets all the additional data pattern records of the flow.
--      If flow of the given name does not exists one record with status 40 is returned.
--
-- Parameters:
--      i_flow_name             - Name of the flow which additional data patterns to get         -
--
-- Returns:
--      status                  - Status code
--      status_text             - Status text
--      id_fp_additional_data   - Id of the additional data entry
--      ad_name                 - name of the additional data entry
--      ad_default_value        - default value of the additional data entry
--      created_by              - user who created the entry
--      created_when            - when was the entry created
--      edited_by               - user who last edited the entry
--      edited_when             - when was the last edit of the entry
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
        SELECT 10, 'OK', AD.id_fp_additional_data, AD.ad_name,
               AD.ad_default_value, AD.created_by, AD.created_at, AD.updated_by,
               AD.updated_at
        FROM flow_patterns.additional_data AD
        WHERE AD.key_fp_flow = _key_fp_flow;
    ELSE
        RETURN NEXT;
    END IF;

    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION flow_patterns.get_flow_additional_data(TEXT) TO atum_configurator;
