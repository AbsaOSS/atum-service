/*
 * Copyright 2021 ABSA Group Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

CREATE OR REPLACE FUNCTION runs.get_flow_additional_data(
    IN  i_id_flow             BIGINT,
    OUT status                INTEGER,
    OUT status_text           TEXT,
    OUT flow_name             TEXT,
    OUT id_segmentation       BIGINT,
    OUT segmentation          HSTORE,
    OUT id_additional_data    BIGINT,
    OUT ad_name               TEXT,
    OUT ad_value              TEXT,
    OUT created_by            TEXT,
    OUT created_at            TIMESTAMP WITH TIME ZONE,
    OUT updated_by            TEXT,
    OUT updated_at            TIMESTAMP WITH TIME ZONE
) RETURNS SETOF record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs.get_flow_additional_data(1)
--      For the given flow the function returns additional data info together with basic segmentation info and status.
--      If the given flow does not exist, exactly one record is returned with the error status.
--
-- Parameters:
--      i_id_flow           - Flow id
--
-- Returns:
--      status              - Status code
--      status_text         - Status text
--      flow_name           - Flow name
--      id_segmentation     - Segmentation id
--      segmentation        - Segmentation
--      id_additional_data  - Additional data id
--      ad_name             - Additional data name
--      ad_value            - Additional data value
--      created_by          - Additional data created by
--      created_at          - Additional data created at
--      updated_by          - Additional data updated by
--      updated_at          - Additional data updated at
--
-- Status codes:
--      10                  - OK
--      41                  - Flow not found
--
-------------------------------------------------------------------------------
DECLARE
    _flow_name   TEXT;
BEGIN
    _flow_name = (SELECT flow_name FROM runs.flows WHERE id_flow = i_id_flow);

    IF _flow_name IS NULL THEN
        status := 41;
        status_text := 'Flow not found';
        RETURN NEXT;
        RETURN;
    END IF;

    RETURN QUERY
    SELECT 10, 'OK', _flow_name AS flow_name, sgm.id_segmentation, sgm.segmentation, ad.id_additional_data,
    ad.ad_name, ad.ad_value, ad.created_by, ad.created_at, ad.updated_by, ad.updated_at
    FROM runs.segmentation_to_flow sgmtf
    INNER JOIN runs.segmentations sgm
      ON sgmtf.key_segmentation = sgm.id_segmentation
    INNER JOIN runs.additional_data ad
      ON sgm.id_segmentation = ad.key_segmentation
    WHERE sgmtf.key_flow = i_id_flow;

END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION runs.get_flow_additional_data(BIGINT) TO atum_user;
