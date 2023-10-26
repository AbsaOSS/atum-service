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

CREATE OR REPLACE FUNCTION runs.open_checkpoint(
    IN  i_segmentation          HSTORE,
    IN  i_id_checkpoint         UUID,
    IN  i_checkpoint_name       TEXT,
    IN  i_workflow_name         TEXT,
    IN  i_process_start_time    TIMESTAMP WITH TIME ZONE,
    IN  i_by_user               TEXT,
    OUT status                  INTEGER,
    OUT status_text             TEXT
) RETURNS record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs.open_checkpoint(6)
--      Writes a  checkpoint overall data, keeping the process_end_time as NULL.
--      Idempotent, can be called repeatedly.
--
-- Parameters:
--      i_segmentation          - segmentation the checkpoint belongs to
--      i_id_checkpoint         - universal id identifying the checkpoint (for idempotence purposes)
--      i_checkpoint_name       - name of the checkpoint
--      i_workflow_name         - workflow the checkpoint belongs to
--      i_process_start_time    - start of the checkpoint computation
--      i_by_user               - user behind the change
--      i_process_end_time      - end of the checkpoint computation
--
-- Returns:
--      status              - Status code
--      status_text         - Status text
--
-- Status codes:
--      11                  - Checkpoint created
--      14                  - Checkpoint already exists
--      30                  - The checkpoint exists with different segmentation
--      41                  - Segmentation does not exist
--
-------------------------------------------------------------------------------
DECLARE
    _key_segmentation               BIGINT;
    _checkpoint_key_segmentation    BIGINT;
BEGIN
    _key_segmentation = runs._get_key_segmentation(i_segmentation);

    IF _key_segmentation IS NULL THEN
        status := 41;
        status_text := 'Segmentation does not exist';
        RETURN;
    END IF;

    SELECT CP.key_segmentation
    FROM runs.checkpoints CP
    WHERE CP.id_checkpoint = i_id_checkpoint
    INTO _checkpoint_key_segmentation;

    IF NOT found THEN
        INSERT INTO runs.checkpoints
            (id_checkpoint, key_segmentation, checkpoint_name, workflow_name, process_start_time, created_by)
        VALUES (i_id_checkpoint, _key_segmentation, i_checkpoint_name, i_workflow_name, i_process_start_time, i_by_user);

        status := 11;
        status_text := 'Checkpoint created';
    ELSE
        IF _key_segmentation != _checkpoint_key_segmentation THEN
            status := 30;
            status_text := 'The checkpoint exists with different segmentation';
            RETURN;
        ELSE
            status := 14;
            status_text := 'Checkpoint already exists';
        END IF;
    END IF;

    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION runs.open_checkpoint
    (HSTORE, UUID, TEXT, TEXT, TIMESTAMP WITH TIME ZONE, TEXT) TO atum_user;
