CREATE OR REPLACE FUNCTION runs.write_checkpoint_end(
    IN  i_segmentation          HSTORE,
    IN  i_id_checkpoint         UUID,
    IN  i_process_end_time      TIMESTAMP WITH TIME ZONE,
    OUT status                  INTEGER,
    OUT status_text             TEXT
) RETURNS record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs.write_checkpoint_end(2)
--      Writes the processing end time of the checkpoint (presumably for additive measures)
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
--      200     - OK
--
-------------------------------------------------------------------------------
DECLARE
    _key_segmentation   BIGINT;
BEGIN
    IF i_process_end_time IS NULL THEN
        -- TODO error status
        RETURN;
    END IF;

    --TODO check segmentation
    _key_segmentation := runs._get_key_segmentation(i_segmentation);

    PERFORM 1
    FROM runs.checkpoints CP
    WHERE CP.id_checkpoint = i_id_checkpoint AND
          CP.key_segmentation = _key_segmentation;

    IF NOT found THEN
        -- TODO error segmentation mismatch
        RETURN;
    END IF;

    UPDATE runs.checkpoints
    SET process_end_time = i_process_end_time
    WHERE id_checkpoint = i_id_checkpoint;

    -- TODO status

    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION runs.write_checkpoint_end(HSTORE, UUID, TIMESTAMP WITH TIME ZONE) TO atum_user;
