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

CREATE OR REPLACE FUNCTION flow_patterns.set_additional_data(
    IN  i_flow_name             TEXT,
    IN  i_ad_name               TEXT,
    IN  i_ad_default_value      TEXT,
    IN  i_by_user               TEXT,
    OUT status                  INTEGER,
    OUT status_text             TEXT,
    OUT id_fp_additional_data   BIGINT,
    OUT key_fp_flow             BIGINT
) RETURNS record AS
$$
-------------------------------------------------------------------------------
--
-- Function: flow_patterns.set_additional_data(4)
--      Adds an additional data pattern for the given flow.
--
-- Parameters:
--      i_flow_name             - flow name the entry belongs to
--      i_ad_name               - name of the additional data entry
--      i_ad_default_value      - default value of the additional data entry
--      i_by_user               - user initiating the change

--
-- Returns:
--      status                  - Status code
--      status_text             - Status text
--      id_fp_additional_data   - id of the additional data pattern entry
--      key_fp_flow             - id of the flow the additional data pattern were added into
--
-- Status codes:
--      11                  - Additional data entry created
--      41                  - Flow not found
--
-------------------------------------------------------------------------------
DECLARE
    _id_fp_additional_data  BIGINT;
BEGIN
    SELECT GF.status, GF.status_text, GF.id_fp_flow
    FROM flow_patterns.get_flow(i_flow_name) GF
    INTO status, status_text, key_fp_flow;

    IF status = 10 THEN
        INSERT INTO flow_patterns.additional_data(key_fp_flow, ad_name, ad_default_value, created_by, updated_by)
        VALUES (key_fp_flow, i_ad_name, i_ad_default_value, i_by_user, i_by_user)
        RETURNING flow_patterns.additional_data.id_fp_additional_data
            INTO id_fp_additional_data;

        status := 11;
        status_text := 'Additional data entry created';
    ELSE
        -- flow not found, status_text can stay
        status := 41;
    END IF;

    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION flow_patterns.set_additional_data(TEXT, TEXT, TEXT, TEXT) TO atum_configurator;
