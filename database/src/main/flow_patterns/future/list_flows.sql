/*
 * Copyright 2022 ABSA Group Limited
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

CREATE OR REPLACE FUNCTION flow_patterns.list_flows(
    OUT id_fp_flow          BIGINT,
    OUT flow_name           TEXT,
    OUT flow_description    TEXT,
    OUT created_by          TEXT,
    OUT created_when        TIMESTAMP WITH TIME ZONE,
    OUT edited_by           TEXT,
    OUT edited_when         TIMESTAMP WITH TIME ZONE
) RETURNS SETOF record AS
$$
-------------------------------------------------------------------------------
--
-- Function: flow_patterns.list_flows(0)
--      Lists all exsiting flows
--
-- Parameters:
--      i_parameter         -
--
-- Returns:
--      id_fp_flow          - id of the flow
--      flow_name           - name of the flow,
--      flow_description    - description of the flow
--      created_by          - user who created the flow
--      created_when        - when was the flow created
--      edited_by           - user who last edited the flow
--      edited_when         - when was the last edit of the flow
--
-- Status codes:
--      No status codes used, set is empty if no
--
-------------------------------------------------------------------------------
DECLARE
BEGIN
    RETURN QUERY
    SELECT F.id_fp_flow, F.flow_name, F.flow_description, F.created_by,
        F.created_when, F.created_by, F.edited_by, F.edited_when
    FROM flow_patterns.flows F
    ORDER BY F.flow_name;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION flow_patterns.list_flows() TO atum_configurator;
