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

CREATE OR REPLACE FUNCTION flow_patterns.update_measure_definition(
    IN  i_id_fp_measure_definition   TEXT,
    IN  i_measure_type                          TEXT,
    IN  i_measure_fields                        TEXT[],
    IN  i_by_user                               TEXT,
    OUT status                                  INTEGER,
    OUT status_text                             TEXT
) RETURNS record AS
$$
-------------------------------------------------------------------------------
--
-- Function: flow_patterns.update_measure_definition(4)
--      Updates the measure definition pattern values
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
--      12                  - Measure definition entry updated
--      14                  - Measure definition entry has not changed
--      40                  - Measure definition entry not found
--
-------------------------------------------------------------------------------
DECLARE
    _measure_type     TEXT;
    _measure_fields   TEXT;
BEGIN
    SELECT CMD.measure_type, CMD.measure_fields
    FROM flow_patterns.measure_definitions CMD
    WHERE CMD.id_fp_measure_definition = i_id_fp_measure_definition
    FOR UPDATE
    INTO _measure_type, _measure_fields;

    IF NOT found THEN
        status := 40;
        status_text := 'Measure definition entry not found';
        RETURN;
    END IF;

    IF (i_measure_type IS DISTINCT FROM _measure_type) OR (i_measure_fields IS DISTINCT FROM _measure_fields) THEN
        UPDATE flow_patterns.measure_definitions
        SET measure_type = i_measure_type,
            measure_fields = i_measure_fields,
            updated_by = i_by_user,
            updated_at = now()
        WHERE id_fp_measure_definition = i_id_fp_measure_definition;

        status := 12;
        status_text := 'Measure definition entry updated';
    ELSE
        status := 14;
        status_text := 'Measure definition entry has not changed';
    END IF;

    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION flow_patterns.update_measure_definition(TEXT, TEXT, TEXT[], TEXT) TO atum_configurator;
