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

CREATE OR REPLACE FUNCTION flows._add_to_parent_flows(
    IN  i_fk_parent_partitioning    BIGINT,
    IN  i_fk_partitioning           BIGINT,
    IN  i_by_user                   TEXT,
    OUT status                      INTEGER,
    OUT status_text                 TEXT
) RETURNS record AS
$$
-------------------------------------------------------------------------------
--
-- Function: flows._add_to_parent_flows(3)
--      Add partitioning to the flows of parent partitioning
--
-- Parameters:
--      i_fk_parent_partitioning    - id of the parent partitioning
--      i_fk_partitioning           - id of the partitioning to add to the flows
--      i_by_user                   - user behind the change
--
-- Returns:
--      status              - Status code
--      status_text         - Status text
--
-- Status codes:
--      11                  - Partitioning added to flows
--      14                  - Nothing has changed
--
-------------------------------------------------------------------------------
DECLARE
BEGIN

    INSERT INTO flows.partitioning_to_flow (fk_flow, fk_partitioning, created_by)
    SELECT PTF.fk_flow, i_fk_partitioning, i_by_user
    FROM flows.partitioning_to_flow PTF
    WHERE PTF.fk_partitioning = i_fk_parent_partitioning
    ON CONFLICT DO NOTHING;

    IF found THEN
        status := 11;
        status_text := 'Partitioning added to flows';
    ELSE
        status := 14;
        status_text := 'Nothing has changed';
    END IF;

    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION flows._add_to_parent_flows(BIGINT, BIGINT, TEXT) TO atum_owner;
