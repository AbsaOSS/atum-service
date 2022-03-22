CREATE OR REPLACE FUNCTION runs.define_flow(
    IN  i_segmentation      HSTORE,
    IN  i_by_user           TEXT,
    OUT status              INTEGER,
    OUT status_text         TEXT
) RETURNS record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs.define_flow(2)
--      [Description]
--
-- Parameters:
--      i_segmentation      - segmentation fo the flow
--      i_by_user           - user initiating the linking
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
    _segmentation_id    BIGINT;
BEGIN
    _segmentation_id = runs._get_key_segmentation(i_segmentation);

    IF _segmentation_id IS NULL THEN
        PERFORM 1
        FROM runs._add_segmentation_flow(i_segmentation, i_by_user, TRUE, NULL);
    ELSE
    END IF;

    -- TODO statuses & return values

    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION runs.define_flow(HSTORE, TEXT) TO atum_user;

CREATE OR REPLACE FUNCTION runs.define_flow(
    IN  i_parent_segmentation   HSTORE,
    IN  i_sub_segmentation      HSTORE,
    IN  i_by_user               TEXT,
    OUT status              INTEGER,
    OUT status_text         TEXT
) RETURNS record AS
$$
    -------------------------------------------------------------------------------
--
-- Function: runs.define_flow(3)
--      [Description]
--
-- Parameters:
--      i_key_user                - skypename of the user
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
    _key_parent_segmentation        BIGINT;
    _segmentation                   HSTORE;
    _id_segmentation                BIGINT;
    _take_pattern_additional_data   BOOLEAN;
    _segmentation_id                BIGINT;
BEGIN
    _key_parent_segmentation := runs._get_key_segmentation(i_parent_segmentation);

    IF _key_parent_segmentation IS NULL THEN
        -- TODO error status parent does not exist
        RETURN;
    END IF;


    _segmentation := i_parent_segmentation || i_sub_segmentation;

    IF i_parent_segmentation = _segmentation THEN
        -- TODO error i_sub_segmentation cannot be empty
        RETURN;
    END IF;

    _segmentation_id := runs._get_key_segmentation(_segmentation);

    IF _segmentation_id IS NULL THEN
        _take_pattern_additional_data := runs._get_flow_name_from_segmentation(i_parent_segmentation) IS NULL;

        SELECT ADF.id_segmentation
        FROM runs._add_segmentation_flow(_segmentation, i_by_user,  _take_pattern_additional_data,_key_parent_segmentation) ADF
        INTO _id_segmentation;

        INSERT INTO runs.segmentation_to_flow (key_flow, key_segmentation, created_by)
        SELECT STF.key_flow, _id_segmentation, i_by_user
        FROM runs.segmentation_to_flow STF
        WHERE STF.key_segmentation = _key_parent_segmentation;

        INSERT INTO runs.checkpoint_measure_definitions (key_segmentation, measure_type, measure_fields, created_by, created_when)
        SELECT _id_segmentation, CMD.measure_type, CMD.measure_fields, CMD.created_by, CMD.created_when
        FROM runs.checkpoint_measure_definitions CMD
        WHERE CMD.key_segmentation = _key_parent_segmentation
        ON CONFLICT DO NOTHING;
    ELSE
    END IF;

    -- TODO statuses & return values
    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION runs.define_flow(HSTORE, HSTORE, TEXT) TO atum_user;
