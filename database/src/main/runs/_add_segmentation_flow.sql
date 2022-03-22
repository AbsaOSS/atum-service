CREATE OR REPLACE FUNCTION runs._add_segmentation_flow(
    IN  i_segmentation                  HSTORE,
    IN  i_by_user                       TEXT,
    IN  i_take_pattern_additional_data  BOOLEAN,
    IN  i_key_parent_segmentation       BIGINT,
    OUT status                          INTEGER,
    OUT status_text                     TEXT,
    OUT id_segmentation                 BIGINT,
    OUT id_flow                         BIGINT
) RETURNS record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs._add_segmentation_flow(2)
--      Links a flow to the given segmentation. If the segmentation contains a flow name and the flow name exists in
--      patterns the pattern is copied into runs.
--
-- Parameters:
--      i_segmentation                  - the segmentation to link
--      i_by_user                       - user initiating the linking
--      i_take_pattern_additional_data  - if pattern is used to fill the flow, this switch decides if additional data are taken too
--      i_key_parent_segmentation       - optional parent segmentation
--
-- Returns:
--      status             - Status code
--      status_text        - Status text
--
-- Status codes:
--      200     - OK TODO
--
-------------------------------------------------------------------------------
DECLARE
    _id_segmentation    BIGINT;
    _id_flow            BIGINT;
    _flow_name          TEXT;
    _flow_description   TEXT;
    _from_pattern       BOOLEAN;
    _key_fp_flow        BIGINT;
BEGIN

    INSERT INTO runs.segmentations (segmentation, key_parent_segmentation, created_by)
    VALUES (i_segmentation, i_key_parent_segmentation,i_by_user)
    RETURNING id_segmentation
    INTO _id_segmentation;

    _flow_name := runs._get_flow_name_from_segmentation(i_segmentation);

    --generating the id explicitly to use it it custom flow name if needed
    _id_flow := global_id();


    IF _flow_name IS NULL THEN
        _flow_name := 'Custom flow #' || _id_flow;
        _from_pattern := FALSE;
    ELSE
        SELECT F.id_fp_flow, F.flow_description
        FROM flow_patterns.flows F
        WHERE F.flow_name = _flow_name
        INTO _key_fp_flow, _flow_description;

        IF (FOUND) THEN
            _from_pattern := TRUE;
            INSERT INTO runs.additional_data (key_segmentation, ad_name, ad_type, ad_value, created_by, updated_by, updated_when)
            SELECT _id_segmentation, AD.ad_name, AD.ad_type, AD.ad_default_value, i_by_user, AD.updated_by, AD.updated_when
            FROM flow_patterns.additional_data AD
            WHERE AD.key_fp_flow = _key_fp_flow;

            IF i_take_pattern_additional_data THEN
                INSERT INTO runs.checkpoint_measure_definitions (key_segmentation, measure_type, measure_fields, created_by)
                SELECT _id_segmentation, CMD.measure_type, CMD.measure_fields, CMD.updated_by
                FROM flow_patterns.checkpoint_measure_definitions CMD
                WHERE CMD.key_fp_flow = _key_fp_flow;
            END IF;
        ELSE
            _from_pattern := FALSE;
        END IF;

    END IF;

    INSERT INTO runs.flows(id_flow, flow_name, flow_description, from_pattern, created_by)
    VALUES (_id_flow, _flow_name, _flow_description, _from_pattern, i_by_user);

    INSERT INTO runs.segmentation_to_flow (key_flow, key_segmentation, created_by)
    VALUES (_id_flow, _id_segmentation, i_by_user);

    -- TODO statuses
    id_segmentation := _id_segmentation;
    id_flow := _id_flow;

    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;
