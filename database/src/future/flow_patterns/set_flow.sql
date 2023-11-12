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

CREATE OR REPLACE FUNCTION flow_patterns.set_flow(
    IN  i_flow_name         TEXT,
    IN  i_flow_description  TEXT,
    IN  i_by_user           TEXT,
    OUT status              INTEGER,
    OUT status_text         TEXT,
    OUT id_fp_flow          BIGINT
) RETURNS record AS
$$
-------------------------------------------------------------------------------
--
-- Function: flow_patterns.set_flow(3)
--      Sets a flow pattern basic information. If the flow of the given name already exists
--      it will be updated, if not then created.
--
-- Parameters:
--      i_flow_name         - flow name to add/edit
--      i_flow_description  -
--      i_by_user           - user initiating the change
--
-- Returns:
--      status              - Status code
--      status_text         - Status text
--
-- Status codes:
--      11                  - Flow created
--      12                  - Flow updated
--      14                  - Flow has not changed
--
-------------------------------------------------------------------------------
DECLARE
BEGIN
    SELECT F.id_fp_flow
    FROM flow_patterns.flows F
    WHERE flow_name = i_flow_name
    FOR UPDATE
    INTO id_fp_flow;

    IF found THEN
        UPDATE flow_patterns.flows
        SET flow_description = i_flow_description,
            updated_by = i_by_user,
            updated_at = now()
        WHERE flow_name = i_flow_name AND
              flow_description IS DISTINCT FROM i_flow_description;

        IF FOUND THEN
            status := 12;
            status_text := 'Flow updated';
        ELSE
            status := 14;
            status_text := 'Flow has not changed';
        END IF;
    ELSE
        INSERT INTO flow_patterns.flows(flow_name, flow_description, created_by, updated_by)
        VALUES (i_flow_name, i_flow_description, i_by_user, i_by_user)
        RETURNING flow_patterns.flows.id_fp_flow
        INTO id_fp_flow;

        status := 11;
        status_text := 'Flow created';
    END IF;

    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION flow_patterns.set_flow(TEXT, TEXT, TEXT) TO atum_configurator;
