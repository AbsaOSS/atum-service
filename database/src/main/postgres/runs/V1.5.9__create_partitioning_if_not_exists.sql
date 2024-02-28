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

CREATE OR REPLACE FUNCTION runs.create_partitioning_if_not_exists(
    IN  i_partitioning          JSONB,
    IN  i_by_user               TEXT,
    IN  i_parent_partitioning   JSONB = NULL,
    OUT status                  INTEGER,
    OUT status_text             TEXT,
    OUT id_partitioning         BIGINT
) RETURNS record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs.create_partitioning_if_not_exists(3)
--      Creates a partitioning entry if it does not exist
--
-- Parameters:
--      i_partitioning          - partitioning which existence to check
--      i_by_user               - user behind the change
--      i_parent_partitioning   - parent partitioning of the provided partitioning, optional
--
-- Returns:
--      status              - Status code
--      status_text         - Status text
--      id_partitioning     - id of the partitioning
--
-- Status codes:
--      11                  - Partitioning created
--      12                  - Partitioning parent registered
--      14                  - Partitioning already present
--
-------------------------------------------------------------------------------
DECLARE
    _fk_parent_partitioning BIGINT := NULL;
    _create_partitioning    BOOLEAN;
    _status                 BIGINT;
BEGIN

    id_partitioning := runs._get_id_partitioning(i_partitioning, true);

    _create_partitioning := id_partitioning IS NULL;

    IF i_parent_partitioning IS NOT NULL THEN
        SELECT CPINE.id_partitioning
        FROM runs.create_partitioning_if_not_exists(i_parent_partitioning, i_by_user, NULL) AS CPINE
        INTO _fk_parent_partitioning;
    END IF;


    IF _create_partitioning THEN
        INSERT INTO runs.partitionings (partitioning, created_by)
        VALUES (i_partitioning, i_by_user)
        RETURNING partitionings.id_partitioning
        INTO create_partitioning_if_not_exists.id_partitioning;

        PERFORM 1
        FROM flows._create_flow(id_partitioning, i_by_user);

        status := 11;
        status_text := 'Partitioning created';
    ELSE
        status := 14;
        status_text := 'Partitioning already present';
    END IF;

    IF i_parent_partitioning IS NOT NULL THEN

        SELECT ATPF.status
        FROM flows._add_to_parent_flows(_fk_parent_partitioning, id_partitioning, i_by_user) AS ATPF
        INTO _status;

        IF _create_partitioning THEN
          -- copying measure definitions to establish continuity
          INSERT INTO runs.measure_definitions(fk_partitioning, measure_name, measured_columns, created_by, created_at)
          SELECT id_partitioning, CMD.measure_name, CMD.measured_columns, CMD.created_by, CMD.created_at
          FROM runs.measure_definitions CMD
          WHERE CMD.fk_partitioning = _fk_parent_partitioning;

          -- additional data are not copied, they are specific for particular partitioning
        ELSIF (_status = 11) THEN
          status := 12;
          status_text := 'Partitioning parent registered';
        END IF;
    END IF;

    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE  SECURITY DEFINER;

ALTER FUNCTION runs.create_partitioning_if_not_exists(JSONB, TEXT, JSONB) OWNER TO atum_owner;
GRANT EXECUTE ON FUNCTION runs.create_partitioning_if_not_exists(JSONB, TEXT, JSONB) TO atum_user;
