/*
 * Copyright 2023 ABSA Group Limited
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
    IN  i_parent_partitioning   JSONB,
    IN  i_by_user               TEXT,
    OUT status                  INTEGER,
    OUT status_text             TEXT,
    OUT id_partitioning         BIGINT
) RETURNS record AS
$$
    -------------------------------------------------------------------------------
--
-- Function: runs.create_partitioning_if_not_exists(3)
--      [Description]
--
-- Parameters:
--      i_parameter         -
--
-- Returns:
--      status              - Status code
--      status_text         - Status text
--
-- Status codes:
--      11                  - Partitioning created
--      12                  - Partitioning updated
--      14                  - Partitioning already present
--
-------------------------------------------------------------------------------
DECLARE
BEGIN

    SELECT P.id_partitioning
    FROM runs.partitionings P
    WHERE P.partitioning = i_partitioning
    INTO id_partitioning;

    --TODO add to flow

    IF NOT found THEN
        INSERT INTO runs.partitionings (partitioning, created_by)
        VALUES (i_partitioning, i_by_user)
        RETURNING id_partitioning
        INTO id_partitioning;

        status := 11;
        status_text := 'Partitioning created';
    ELSE
        status := 14;
        status_text := 'Partitioning already present';
    END IF;

    IF i_parent_partitioning IS NOT NULL THEN
        -- TODO
        IF status = 14 THEN
            status := 12;
            status_text := 'Partitioning updated';
        END IF;
    END IF;

    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE  SECURITY DEFINER;

ALTER FUNCTION runs.create_partitioning_if_not_exists(JSONB, JSONB, TEXT) OWNER TO atum_owner;
GRANT EXECUTE ON FUNCTION runs.create_partitioning_if_not_exists(JSONB, JSONB, TEXT) TO atum_user;
