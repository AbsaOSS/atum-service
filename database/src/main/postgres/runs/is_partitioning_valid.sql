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

CREATE OR REPLACE FUNCTION runs.is_partitioning_valid(
    IN i_partitioning   JSONB,
    IN i_strict_check   BOOLEAN = true
) RETURNS BOOLEAN AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs.is_partitioning_valid(2)
--      Validates the input partitioning and raises exception if it's not valid.
--
-- Parameters:
--      i_partitioning      - partitioning to check, a valid example:
--                            {
--                              "keys": ["one", "two", "three"],
--                              "version": 1,
--                              "keysToValues": {
--                                  "one": "DatasetA",
--                                  "two": "Version1",
--                                  "three": "2022-12-20"
--                               }
--                            }
--      i_strict_check      - flag signaling whether the partitioning check should be strict or no.
--                            E.g., if an input partitioning is just a pattern, then values can have NULLs and
--                            such check would be skipped.
--
-------------------------------------------------------------------------------
DECLARE

BEGIN
    PERFORM runs.validate_partitioning(i_partitioning, i_strict_check)
    LIMIT 1;

    IF found THEN
        RAISE EXCEPTION 'The input partitioning is not valid: %', jsonb_pretty(i_partitioning);
    END IF;

    RETURN TRUE;
END;
$$
LANGUAGE plpgsql IMMUTABLE SECURITY DEFINER;

ALTER FUNCTION runs.is_partitioning_valid(JSONB, BOOL) OWNER TO atum_owner;
