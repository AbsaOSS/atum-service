CREATE OR REPLACE FUNCTION runs.write_measure(
    IN  i_segmentation          HSTORE,
    IN  i_id_measure            UUID,
    IN  i_id_checkpoint         UUID,
    IN  i_measure_type          TEXT,
    IN  i_measure_fields        TEXT[],
    IN  i_value                 TEXT,
    IN  i_by_user               TEXT,
    OUT status              INTEGER,
    OUT status_text         TEXT
) RETURNS record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs.write_measure(7)
--      Adds the measure of the specified checkpoint. If the measure definition does not exists, it will be created.
--
-- Parameters:
--      i_segmentation          - segmentation the measure belongs to
--      i_id_measure            - unique identifier of the measure to ensure idempotence
--      i_id_checkpoint         - reference to the checkpoint this measure belongs into
--      i_measure_type          - type of the measure
--      i_measure_fields        - set of fields the measure is applied on
--      i_value                 - value of the measure
--      i_by_user               - user behind the change
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
    _key_segmentation                   BIGINT;
    _key_checkpoint_measure_definition  BIGINT;
BEGIN

    PERFORM 1
    FROM runs.measures M
    WHERE M.id_measure = i_id_measure;

    IF found THEN
        --TODO already there status code
        RETURN;
    END IF;

    _key_segmentation = runs._get_key_segmentation(i_segmentation);

    IF _key_segmentation IS NULL THEN
        --TODO error
        RETURN;
    END IF;

    PERFORM 1
    FROM runs.checkpoints CP
    WHERE CP.id_checkpoint = i_id_checkpoint AND
          CP.key_segmentation = _key_segmentation;

    IF NOT found THEN
        --TODO error on missing checkpoint
        RETURN;
    END IF;

    SELECT CMD.id_checkpoint_measure_definition
    FROM runs.checkpoint_measure_definitions CMD
    WHERE CMD.key_segmentation = _key_segmentation AND
          CMD.measure_type = i_measure_type AND
          CMD.measure_fields = i_measure_fields
    INTO _key_checkpoint_measure_definition;

    IF NOT found THEN
        INSERT INTO runs.checkpoint_measure_definitions (key_segmentation, measure_type, measure_fields, created_by)
        VALUES (_key_segmentation, i_measure_type, i_measure_fields, i_by_user)
        RETURNING id_checkpoint_measure_definition
        INTO _key_checkpoint_measure_definition;
    END IF;

    INSERT INTO runs.measures (id_measure, key_checkpoint_measure_definition, key_checkpoint, value, value_whole_number)
    VALUES (i_id_measure, _key_checkpoint_measure_definition, i_id_checkpoint, i_value, 0);

    --TODO status codes
    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION runs.write_measure(HSTORE, UUID, UUID, TEXT, TEXT[], TEXT, TEXT) TO atum_user;
