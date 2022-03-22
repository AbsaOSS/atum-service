CREATE OR REPLACE FUNCTION runs._get_key_segmentation(
    IN  i_segmentation      HSTORE,
    OUT key_segmentation    BIGINT
) RETURNS BIGINT AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs._get_key_segmentation(1)
--      Gets the id of the provided segmentation, if it exits
--
-- Parameters:
--      i_segmentation      - segmentation to look for
--
-- Returns:
--      key_segmentation    - id of the segmentation if it exists, NULL otherwise
--
-------------------------------------------------------------------------------
DECLARE
BEGIN
    SELECT SEG.id_segmentation
    FROM runs.segmentations SEG
    WHERE SEG.segmentation = i_segmentation
    INTO key_segmentation;

    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION runs._get_key_segmentation(hstore) TO atum_user;