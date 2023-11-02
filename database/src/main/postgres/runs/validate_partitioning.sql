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

CREATE OR REPLACE FUNCTION runs.validate_partitioning(
    IN  i_partitioning          JSONB,
    IN  i_parent_partitioning   JSONB = NULL,
    OUT status                  INTEGER,
    OUT status_text             TEXT
) RETURNS record AS
$$
    -------------------------------------------------------------------------------
--
-- Function: runs.validate_partitioning(1)
--      Validate partitioning correctness
--
-- Parameters:
--      i_partitioning          - partitioning which validity to check
--
-- Returns:
--      status              - Status code
--      status_text         - Status text
--
-- Status codes:
--      TODO                  - TODO
--
-------------------------------------------------------------------------------
DECLARE
    _fk_parent_partitioning BIGINT := NULL;
    _create_partitioning    BOOLEAN;
    _status                 BIGINT;
BEGIN

    -- Are there unique keys?
    -- Is there the same number of keys and values?
    -- Are keys case insensitive?
    -- Parent partitioning - is it a valid parent?
    --      parent has less keys then the child
    --      all parent keys are present in the head of the child, same order
    --      all values of the parent keys are same for the child
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

ALTER FUNCTION runs.validate_partitioning(JSONB, JSONB, TEXT) OWNER TO atum_owner;
GRANT EXECUTE ON FUNCTION runs.validate_partitioning(JSONB, JSONB, TEXT) TO atum_user;
