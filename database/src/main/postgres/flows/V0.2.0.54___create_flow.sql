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

CREATE OR REPLACE FUNCTION flows._create_flow(
    IN  i_fk_partitioning           BIGINT,
    IN  i_by_user                   TEXT,
    OUT status                      INTEGER,
    OUT status_text                 TEXT,
    OUT id_flow                     BIGINT
) RETURNS record AS
$$
-------------------------------------------------------------------------------
--
-- Function: flows._create_flow(2)
--      Creates a flow associated with provided partitioning
--
-- Parameters:
--      i_fk_partitioning           - id of the partitioning to associate with
--      i_by_user                   - user behind the change
--
-- Returns:
--      status              - Status code
--      status_text         - Status text
--      id_flow             - id of the created flow
--
-- Status codes:
--      11                  - Flow created
--
--
-------------------------------------------------------------------------------
DECLARE
    _id_flow        BIGINT;
    _flow_name      TEXT;
BEGIN
    --generating the id explicitly to use, if custom flow name if needed
    _id_flow := global_id();
    _flow_name := 'Custom flow #' || _id_flow;


    INSERT INTO flows.flows (id_flow, flow_name, flow_description, from_pattern, created_by, fk_primary_partitioning)
    VALUES (_id_flow, _flow_name, '', false, i_by_user, i_fk_partitioning);

    INSERT INTO flows.partitioning_to_flow(fk_flow, fk_partitioning, created_by)
    VALUES (_id_flow, i_fk_partitioning, i_by_user);

    status := 11;
    status_text := 'Flow created';
    id_flow := _id_flow;

    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION flows._create_flow(BIGINT, TEXT) TO atum_owner;
