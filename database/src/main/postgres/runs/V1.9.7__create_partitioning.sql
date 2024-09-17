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

CREATE OR REPLACE FUNCTION runs.create_partitioning(
    IN  i_partitioning              JSONB,
    IN  i_by_user                   TEXT,
    IN  i_parent_partitioning_id    BIGINT = NULL,
    OUT status                      INTEGER,
    OUT status_text                 TEXT,
    OUT id_partitioning             BIGINT
) RETURNS record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs.create_partitioning(3)
--      Creates a partitioning entry
--
-- Parameters:
--      i_partitioning              - partitioning to create or which existence to check
--      i_by_user                   - user behind the change
--      i_parent_partitioning_id    - (optional) parent partitioning id
--
-- Returns:
--      status              - Status code
--      status_text         - Status text
--      id_partitioning     - id of the partitioning
--
-- Status codes:
--      11                  - Partitioning created
--      12                  - Partitioning created with parent partitioning
--      31                  - Partitioning already exists
--      41                  - Parent partitioning not found
--
-------------------------------------------------------------------------------
BEGIN
    id_partitioning := runs._get_id_partitioning(i_partitioning, true);

    IF id_partitioning IS NOT NULL THEN
        status := 31;
        status_text := 'Partitioning already exists';
        RETURN;
    END IF;

    IF i_parent_partitioning_id IS NOT NULL THEN
        PERFORM 1 FROM runs.partitionings P WHERE P.id_partitioning = i_parent_partitioning_id;
        IF NOT FOUND THEN
            status := 41;
            status_text := 'Parent partitioning not found';
            RETURN;
        END IF;
    END IF;

    INSERT INTO runs.partitionings (partitioning, created_by)
    VALUES (i_partitioning, i_by_user)
    RETURNING partitionings.id_partitioning INTO create_partitioning.id_partitioning;

    PERFORM 1 FROM flows._create_flow(id_partitioning, i_by_user);
    status := 11;
    status_text := 'Partitioning created';

    IF i_parent_partitioning_id IS NOT NULL THEN
        PERFORM 1 FROM flows._add_to_parent_flows(i_parent_partitioning_id, id_partitioning, i_by_user);

        -- copying measure definitions to establish continuity
        INSERT INTO runs.measure_definitions(fk_partitioning, measure_name, measured_columns, created_by, created_at)
        SELECT id_partitioning, CMD.measure_name, CMD.measured_columns, CMD.created_by, CMD.created_at
        FROM runs.measure_definitions CMD
        WHERE CMD.fk_partitioning = i_parent_partitioning_id;

        status := 12;
        status_text := 'Partitioning created with parent partitioning';
    END IF;

    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE  SECURITY DEFINER;

ALTER FUNCTION runs.create_partitioning(JSONB, TEXT, BIGINT) OWNER TO atum_owner;
GRANT EXECUTE ON FUNCTION runs.create_partitioning(JSONB, TEXT, BIGINT) TO atum_user;
