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

CREATE OR REPLACE FUNCTION runs.get_partitioning(
    IN  i_partitioning          JSONB,
    OUT status                  INTEGER,
    OUT status_text             TEXT,
    OUT id                      BIGINT,
    OUT o_partitioning          JSONB,
    OUT author                  TEXT
) RETURNS record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs.get_partitioning(1)
--      Retrieves a partitioning by its JSONB representation.
--
-- Parameters:
--      i_partitioning      - partitioning to search for, a valid example:
--                            {
--                              "keys": ["one", "two", "three"],
--                              "version": 1,
--                              "keysToValues": {
--                                  "one": "DatasetA",
--                                  "two": "Version1",
--                                  "three": "2022-12-20"
--                               }
--                            }
--
-- Returns:
--      status              - status of the operation:
--      status_text         - textual representation of the status
--      id                  - ID of the partitioning
--      o_partitioning      - partitioning data
--      author              - author of the partitioning
--
-- Status codes:
--      11                  - OK
--      41                  - Partitioning not found
--
-------------------------------------------------------------------------------
BEGIN
    -- Initialize status and status_text
    status := 41;
    status_text := 'Partitioning not found';

    -- Retrieve partitioning ID
    id := runs._get_id_partitioning(i_partitioning);

    -- If ID is found, retrieve partitioning details
    IF id IS NOT NULL THEN
        SELECT GPBI.id, GPBI.partitioning, GPBI.author
        INTO get_partitioning.id, get_partitioning.o_partitioning, get_partitioning.author
        FROM runs.get_partitioning_by_id(id) AS GPBI;

        -- Update status if partitioning is found
        IF FOUND THEN
            status := 11;
            status_text := 'OK';
        END IF;
    END IF;

    RETURN;
END;
$$
LANGUAGE plpgsql IMMUTABLE SECURITY DEFINER;

ALTER FUNCTION runs.get_partitioning(i_partitioning JSONB) OWNER TO atum_owner;
GRANT EXECUTE ON FUNCTION runs.get_partitioning(i_partitioning JSONB) TO atum_user;
