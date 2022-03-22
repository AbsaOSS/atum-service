CREATE OR REPLACE FUNCTION runs.write_additional_data(
    IN  i_segmentation      HSTORE,
    IN  i_ad_name           TEXT,
    IN  i_ad_value          TEXT,
    IN  i_by_user           TEXT,
    OUT status              INTEGER,
    OUT status_text         TEXT
) RETURNS record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs.write_additional_data(4)
--      Adds the additional data for the segmentation. If additioanl data of the give name already exists for the
--      segmentation, the value is updated.
--
-- Parameters:
--      i_segmentation      - segmentation to add the additional data for
--      i_ad_name           - name of the additional data entry
--      i_ad_value          - value of the additional data entry
--      i_by_user           - user behind the change
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

    _key_segmentation := runs._get_key_segmentation(i_segmentation);

    IF _key_segmentation IS NULL THEN
        -- TODO error
        RETURN;
    END IF;


    UPDATE runs.additional_data
    SET ad_value = i_ad_value,
        updated_by = i_by_user,
        updated_when = now()
    WHERE key_segmentation = _key_segmentation AND
          ad_name = i_ad_name;

    IF NOT found THEN
        -- TODO locking
        INSERT INTO runs.additional_data (key_segmentation, ad_name, ad_value, created_by, updated_by)
        VALUES (_key_segmentation, i_ad_name, i_ad_value, i_by_user, i_by_user);
    END IF;

    --TODO status code
    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION runs.write_additional_data(HSTORE, TEXT, TEXT, TEXT) TO atum_user;
