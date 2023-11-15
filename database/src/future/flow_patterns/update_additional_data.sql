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

CREATE OR REPLACE FUNCTION flow_patterns.insert_additional_data(
    IN  i_id_fp_additional_data TEXT,
    IN  i_ad_name               TEXT,
    IN  i_ad_default_value      TEXT,
    IN  i_by_user               TEXT,
    OUT status                  INTEGER,
    OUT status_text             TEXT
) RETURNS record AS
$$
-------------------------------------------------------------------------------
--
-- Function: flow_patterns.insert_additional_data(4)
--      Updates the additional data pattern values
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
--      12                  - Additional data entry updated
--      14                  - Additional data entry has not changed
--      40                  - Additional data entry not found
--
-------------------------------------------------------------------------------
DECLARE
    _ad_name            TEXT;
    _ad_default_value   TEXT;
BEGIN
    SELECT AD.ad_name, AD.ad_default_value
    FROM flow_patterns.additional_data AD
    WHERE AD.id_fp_additional_data = i_id_fp_additional_data
    FOR UPDATE
    INTO _ad_name, _ad_default_value;

    IF NOT found THEN
        status := 40;
        status_text = 'Additional data entry not found';
        RETURN;
    END IF;

    IF (i_ad_name IS DISTINCT FROM _ad_name) OR (i_ad_default_value IS DISTINCT FROM _ad_default_value) THEN
        UPDATE flow_patterns.additional_data
        SET ad_name = i_ad_name,
            ad_default_value = i_ad_default_value,
            updated_by = i_by_user,
            updated_at = now()
        WHERE id_fp_additional_data = i_id_fp_additional_data;

        status := 12;
        status_text := 'Additional data entry updated';
    ELSE
        status := 14;
        status_text := 'Additional data entry has not changed';
    END IF;

    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION flow_patterns.insert_additional_data(TEXT, TEXT, TEXT, TEXT) TO atum_configurator;
