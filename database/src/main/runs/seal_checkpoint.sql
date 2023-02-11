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

CREATE OR REPLACE FUNCTION runs.seal_checkpoint(
    IN  i_segmentation                      HSTORE,
    IN  i_id_checkpoint                     UUID,
    IN  i_autosum                           BOOLEAN DEFAULT TRUE,
    OUT status                              INTEGER,
    OUT status_text                         TEXT,
    OUT key_checkpoint_measure_definition   BIGINT NOT NULL DEFAULT global_id(),
    OUT key_segmentation                    BIGINT NOT NULL,
    OUT measure_type                        TEXT NOT NULL,
    OUT measure_fields                      TEXT[] NOT NULL,
) RETURNS SETOF record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs.seal_checkpoint(3)
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
--      10                  - OK
--      !4                  - No partial measurements to sum
--      40                  - Checkpoint not found
--      60                  - Checkpoint has been closed before
--
-------------------------------------------------------------------------------
DECLARE
    _key_segmentation   BIGINT;
    _process_end_time   TIMESTAMP WITH TIME ZONE;
BEGIN
    _key_segmentation := runs._get_key_segmentation(i_segmentation);

    SELECT CP.process_end_time
    FROM runs.checkpoints CP
    WHERE CP.id_checkpoint = i_id_checkpoint AND
          CP.key_segmentation = _key_segmentation;
    INTO _process_end_time;

    IF NOT found THEN
        status := 40;
        status_text := 'Checkpoint not found';
        RETURN NEXT;
        RETURN;
    END IF;

    IF _process_end_time IS NOT NULL THEN
        status := 60;
        status_text := 'Checkpoint has been closed before';
        RETURN NEXT;
        RETURN;
    END IF;


END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION runs.seal_checkpoint() TO [user];
