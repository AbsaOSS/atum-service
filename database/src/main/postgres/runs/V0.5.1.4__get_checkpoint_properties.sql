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

CREATE OR REPLACE FUNCTION runs.get_checkpoint_properties(
    IN i_checkpoint_id             UUID,
    OUT status                     INTEGER,
    OUT status_text                TEXT,
    OUT property_name              TEXT,
    OUT property_value             TEXT
)
    RETURNS SETOF record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs.get_checkpoint_properties(1)
--      Retrieves all properties related to a given checkpoint ID.
--
-- Parameters:
--      i_checkpoint_id          - ID of the checkpoint
--
-- Returns:
--      property_name           - Name of the property
--      property_value          - Value of the property
--      status                  - Status code
--      status_text             - Status message
--
-- Status codes:
--      11 - OK
--      42 - Checkpoint not found
--
-------------------------------------------------------------------------------
BEGIN
  PERFORM 1
  FROM runs.checkpoints C
  WHERE C.id_checkpoint = i_checkpoint_id;

  IF NOT FOUND THEN
    status := 42;
    status_text := 'Checkpoint not found';
    property_name := NULL;
    property_value := NULL;
    RETURN NEXT;
    RETURN;
  END IF;

  RETURN QUERY
    SELECT
      11 AS status,
      'OK' AS status_text,
      CP.property_name,
      CP.property_value
    FROM runs.checkpoint_properties CP
    WHERE CP.fk_checkpoint = i_checkpoint_id;

  IF NOT FOUND THEN
    status := 11;
    status_text := 'OK';
    property_name := NULL;
    property_value := NULL;
    RETURN NEXT;
  END IF;

END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

ALTER FUNCTION runs.get_checkpoint_properties(UUID) OWNER TO atum_owner;
GRANT EXECUTE on FUNCTION runs.get_checkpoint_properties(UUID) TO atum_user;
