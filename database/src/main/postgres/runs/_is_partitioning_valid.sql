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

CREATE OR REPLACE FUNCTION runs._is_partitioning_valid(
    IN i_partitioning   JSONB,
    IN i_is_pattern     BOOL = false,
    OUT status          INTEGER,
    OUT status_text     TEXT
) RETURNS record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs._is_partitioning_valid(2)
--      Validates the input partitioning.
--
-- Parameters:
--      i_partitioning      - partitioning to check, a valid example:
-- 	                          {
--                              "keys": ["one", "two", "three"],
--                              "version": 1,
-- 	                            "keysToValuesMap": {
--                                  "one": "DatasetA",
--                                  "two": "Version1",
-- 	                          	    "three": "2022-12-20"
--                               }
--                            }
--      i_is_pattern        - flag signaling whether the partitioning is not a real one, but just a pattern.
--                            If the input partitioning is just a pattern, then values can have NULL values and
--                            such check would be skipped.
--
-- Returns:
--      status              - Status code
--      status_text         - Status text
--
-- Status codes:
--      10                  - OK
--      50                  - Partitioning not valid
--
-------------------------------------------------------------------------------
DECLARE

BEGIN
    PERFORM runs._validate_partitioning(i_partitioning, i_is_pattern)
    LIMIT 1;

    IF found THEN
        status := 50;
        status_text := 'The input partitioning is not valid';
    ELSE
        status := 10;
        status_text := 'OK';
    END IF;

    RETURN;
END;
$$
LANGUAGE plpgsql IMMUTABLE SECURITY DEFINER;

ALTER FUNCTION runs._is_partitioning_valid(JSONB, BOOL) OWNER TO atum_owner;
