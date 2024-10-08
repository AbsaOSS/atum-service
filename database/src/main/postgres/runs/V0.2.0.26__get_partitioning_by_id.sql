/*
 * Copyright 2021 ABSA Group Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

-- Function: runs.get_partitioning_by_id(Long)
CREATE OR REPLACE FUNCTION runs.get_partitioning_by_id(
    IN i_id                 BIGINT,
    OUT status              INTEGER,
    OUT status_text         TEXT,
    OUT id                  BIGINT,
    OUT partitioning        JSONB,
    OUT author              TEXT
) RETURNS RECORD AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs.get_partitioning_by_id(1)
--      Returns partitioning for the given id
--
-- Parameters:
--      i_id                - id that we asking the partitioning for
--
-- Returns:
--      status              - Status code
--      status_text         - Status message
--      partitioning        - Partitioning value to be returned
--      author              - Author of the partitioning

-- Status codes:
--      11 - OK
--      41 - Partitioning not found
--
-------------------------------------------------------------------------------
BEGIN

    SELECT P.partitioning,
           P.id_partitioning,
           P.created_by
    FROM runs.partitionings AS P
    WHERE P.id_partitioning = i_id
    INTO get_partitioning_by_id.partitioning, id, author;

    IF FOUND THEN
        status := 11;
        status_text := 'OK';
    ELSE
        status := 41;
        status_text := 'Partitioning not found';
    END IF;

    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

ALTER FUNCTION runs.get_partitioning_by_id(BIGINT) OWNER TO atum_owner;
GRANT EXECUTE ON FUNCTION runs.get_partitioning_by_id(BIGINT) TO atum_user;

