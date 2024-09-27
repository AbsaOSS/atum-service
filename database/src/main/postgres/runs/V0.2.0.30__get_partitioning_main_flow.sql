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

CREATE OR REPLACE FUNCTION runs.get_partitioning_main_flow(
    IN i_partitioning_id BIGINT,
    OUT status INTEGER,
    OUT status_text TEXT,
    OUT id_flow BIGINT,
    OUT flow_name TEXT,
    OUT flow_description TEXT,
    OUT from_pattern BOOLEAN
) RETURNS record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs.get_partitioning_main_flow(1)
--      Returns the main flow for the given partitioning
--
-- Parameters:
--      i_partitioning_id   - id of the partitioning for requested main flow
--
-- Returns:
--      status              - Status code
--      status_text         - Status message
--      id_flow             - ID of the main flow associated with a given partitioning
--      flow_name           - Name of the main flow
--      flow_description    - Description of the main flow
--      from_pattern        - Flag if the main flow was generated from pattern
--
-- Status codes:
--      11 - OK
--      41 - Partitioning not found
--      50 - Main flow not found
--
-------------------------------------------------------------------------------

BEGIN
    PERFORM 1 FROM runs.partitionings WHERE id_partitioning = i_partitioning_id;
    IF NOT FOUND THEN
        status := 41;
        status_text := 'Partitioning not found';
        RETURN;
    END IF;

    status = 11;
    status_text = 'OK';

    SELECT F.id_flow,
           F.flow_name,
           F.flow_description,
           F.from_pattern
    FROM flows.flows AS F
    WHERE F.fk_primary_partitioning = i_partitioning_id
    INTO id_flow, flow_name, flow_description, from_pattern;

    IF NOT FOUND THEN
        status := 50;
        status_text := 'Main flow not found';
        RETURN;
    END IF;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

ALTER FUNCTION runs.get_partitioning_main_flow(BIGINT) OWNER TO atum_owner;
GRANT EXECUTE ON FUNCTION runs.get_partitioning_main_flow(BIGINT) TO atum_user;
