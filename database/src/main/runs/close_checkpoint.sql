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

CREATE OR REPLACE FUNCTION runs.close_checkpoint(
    IN  i_segmentation          HSTORE,
    IN  i_id_checkpoint         UUID,
    IN  i_process_end_time      TIMESTAMP WITH TIME ZONE,
    OUT status                  INTEGER,
    OUT status_text             TEXT
) RETURNS record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs.close_checkpoint(2)
--      Writes the processing end time of the checkpoint which prevents adding
--      any more measurements into it.
--
-- Parameters:
--      i_segmentation          - segmentation the checkpoint belongs to
--      i_id_checkpoint         - checkpoint to end
--      i_process_end_time      - end of the checkpoint computation
--
-- Returns:
--      status             - Status code
--      status_text        - Status text
--
-- Status codes:
--      10      - OK
--      14      - Checkpoint has been closed before
--      40      - Checkpoint not found
--      60      - Checkpoint cannot be closed with i_process_end_time NULL
--
-------------------------------------------------------------------------------
DECLARE
    _key_segmentation   BIGINT;
BEGIN
    IF i_process_end_time IS NULL THEN
        status := 60;
        status_text := 'Checkpoint cannot be closed with i_process_end_time NULL';
        RETURN;
    END IF;

    _key_segmentation := runs._get_key_segmentation(i_segmentation);

    PERFORM 1
    FROM runs.checkpoints CP
    WHERE CP.id_checkpoint = i_id_checkpoint AND
          CP.key_segmentation = _key_segmentation;

    IF NOT found THEN
        status := 40;
        status_text := 'Checkpoint not found';
        RETURN;
    END IF;

    UPDATE runs.checkpoints
    SET process_end_time = i_process_end_time
    WHERE id_checkpoint = i_id_checkpoint AND
          process_end_time IS NULL;

    IF found THEN
        status := 10;
        status_text := 'OK';
    ELSE
        status := 14;
        status_text := 'Checkpoint has been closed before';
    END IF;

    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION runs.close_checkpoint(HSTORE, UUID, TIMESTAMP WITH TIME ZONE) TO atum_user;
