CREATE OR REPLACE FUNCTION runs._get_flow_name_from_segmentation(
    IN  i_segmentation      HSTORE,
    OUT flow_name           TEXT
) RETURNS TEXT AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs._get_flow_name_from_segmentation(1)
--      Extacts the flow name key from the segmentation
--
-- Parameters:
--      i_segmentation      - the segmentation to extract
--
-- Returns:
--      flow_name           - the flow name segment or NULL if it's not present
--
-------------------------------------------------------------------------------
DECLARE
BEGIN
    flow_name := i_segmentation -> 'flow_name';
END;
$$
LANGUAGE plpgsql IMMUTABLE SECURITY DEFINER;
