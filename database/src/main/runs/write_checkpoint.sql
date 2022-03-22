CREATE OR REPLACE FUNCTION runs.write_checkpoint(
    IN  i_segmentation          HSTORE,
    IN  i_id_checkpoint         UUID,
    IN  i_checkpoint_name       TEXT,
    IN  i_workflow_name         TEXT,
    IN  i_process_start_time    TIMESTAMP WITH TIME ZONE,
    IN  i_by_user               TEXT,
    IN  i_process_end_time      TIMESTAMP WITH TIME ZONE DEFAULT NULL,

    OUT status                  INTEGER,
    OUT status_text             TEXT
) RETURNS record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs.write_checkpoint(7)
--      Writes a  checkpoint overall data.
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
--      status             - Status code
--      status_text        - Status text
--
-- Status codes:
--      200     - OK
--
-------------------------------------------------------------------------------
DECLARE
    _key_segmentation               BIGINT;
    _checkpoint_key_segmentation    BIGINT;
BEGIN
    _key_segmentation = runs._get_key_segmentation(i_segmentation);

    IF _key_segmentation IS NULL THEN
        --TODO error
        RETURN;
    END IF;

    SELECT CP.key_segmentation
    FROM runs.checkpoints CP
    WHERE CP.id_checkpoint = i_id_checkpoint
    INTO _checkpoint_key_segmentation;

    IF NOT found THEN
        INSERT INTO runs.checkpoints
            (id_checkpoint, key_segmentation, checkpoint_name, workflow_name, process_start_time, process_end_time, created_by)
        VALUES (i_id_checkpoint, _key_segmentation, i_checkpoint_name, i_workflow_name, i_process_start_time, i_process_end_time, i_by_user);

        --TODO status
    ELSE
        IF _key_segmentation != _checkpoint_key_segmentation THEN
            --TODO errot status
            RETURN;
        ELSE
            --TODO already exists status
        END IF;
    END IF;

    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION runs.write_checkpoint
    (HSTORE, UUID, TEXT, TEXT, TIMESTAMP WITH TIME ZONE, TEXT, TIMESTAMP WITH TIME ZONE) TO atum_user;
