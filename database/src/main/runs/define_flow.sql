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
--      Defines a flow by creating a link between the provided segmentation and a newly created flow.
--      Flow is created only if the particular segmentation has not existed yet in the DB.
--
-- Parameters:
--      i_segmentation      - segmentation for the flow
--      i_by_user           - user initiating the linking
--
-- Returns:
--      status              - Status code
--      status_text         - Status text
--
-- Status codes:
--      10                  - OK
--      14                  - Flow already exists
--
-------------------------------------------------------------------------------
DECLARE
    _segmentation_id    BIGINT;
BEGIN
    _segmentation_id = runs._get_key_segmentation(i_segmentation);

    IF _segmentation_id IS NULL THEN
        SELECT ADF.status, ADF.status_text
        FROM runs._add_segmentation_flow(i_segmentation, i_by_user, TRUE, NULL) ADF
        INTO status, status_text;
    ELSE
        status := 14;
        status_text := 'Flow already exists';
    END IF;

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
--      Defines a new sub flow. The segmentation linked to the flow is the union of the provided parent and sub
--      segmentations. Flow is created only if the particular segmentation has not existed yet in the DB.
--      Additionally the segmentation is linked to all the flows the parent segmentation is linked to.
--
-- Parameters:
--      i_parent_segmentation   - segmentation for the flow
--      i_sub_segmentation      - segmentation for the flow
--      i_by_user               - user initiating the linking
--
-- Returns:
--      status              - Status code
--      status_text         - Status text
--
-- Status codes:
--      10                  - OK
--      12                  - Segmentation already defined, flows of parent segmentation added
--      14                  - Flow already exists
--      41                  - Parent segmentation not found
--      50                  - Sub segmentation is empty
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
        status := 41;
        status_text := 'Parent segmentation not found';
        RETURN;
    END IF;


    _segmentation := i_parent_segmentation || i_sub_segmentation;

    IF i_parent_segmentation = _segmentation THEN
        status := 50;
        status_text := 'Sub segmentation is empty';
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

        INSERT INTO runs.checkpoint_measure_definitions (key_segmentation, measure_type, measure_fields, created_by, created_at)
        SELECT _id_segmentation, CMD.measure_type, CMD.measure_fields, CMD.created_by, CMD.created_at
        FROM runs.checkpoint_measure_definitions CMD
        WHERE CMD.key_segmentation = _key_parent_segmentation
        ON CONFLICT DO NOTHING;

        status := 10;
        status_text := 'OK';
    ELSE
        -- segmentation already exists, so only adding parent it to parent's flows
        INSERT INTO runs.segmentation_to_flow (key_flow, key_segmentation, created_by)
        SELECT STF.key_flow, _id_segmentation, i_by_user
        FROM runs.segmentation_to_flow STF
        WHERE STF.key_segmentation = _key_parent_segmentation
        ON CONFLICT DO NOTHING;

        IF found THEN
            status := 12;
            status_text := 'Segmentation already defined, flows of parent segmentation added';
        ELSE
            status := 14;
            status_text := 'Flow already exists';
        END IF;

        -- maybe there are some child segmentations for the segmentation too, at the moment they are not added to the
        -- parent flows; can be extended if the case would emerge
    END IF;

    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION runs.define_flow(HSTORE, HSTORE, TEXT) TO atum_user;
