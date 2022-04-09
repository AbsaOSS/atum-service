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

CREATE OR REPLACE FUNCTION runs.flows_of_segmentation(
    IN  i_segmentation      HSTORE,
    OUT status              INTEGER,
    OUT status_text         TEXT,
    OUT key_flow            BIGINT,
    OUT flow_name           TEXT,
    OUT flow_description    TEXT,
    OUT from_pattern        BOOLEAN,
    OUT created_by          TEXT,
    OUT created_at          TIMESTAMP WITH TIME ZONE,
    OUT segmentation_count  INTEGER
) RETURNS SETOF record AS
$$
    -------------------------------------------------------------------------------
--
-- Function: runs.get_flow_additional_data(1)
--      Returns all the flows the given segmentation belongs to
--
-- Parameters:
--      i_segmentation      - segmentation which flows to get
--
-- Returns:
--      status              - Status code
--      status_text         - Status text
--      key_flow            - id of the flow
--      flow_name           = name of the flow
--      flow_description    - description of the flow
--      from_pattern        - was the flow created based on a pattern
--      created_by          - who created the flow
--      created_at          - when was the flow created
--
-- Status codes:
--      10                  - OK
--      41                  - Segmentation not found
--
-------------------------------------------------------------------------------
DECLARE
    _key_segmentation   BIGINT;
BEGIN
    _key_segmentation = runs._get_key_segmentation(i_segmentation);

    IF _key_segmentation IS NULL THEN
        status := 41;
        status_text := 'Segmentation not found';
        RETURN NEXT;
        RETURN;
    END IF;

    RETURN QUERY
        SELECT 10, 'OK', STF.key_flow, F.flow_name,
               F.flow_description, F.from_pattern, F.created_by, F.created_at,
               (SELECT count(*) FROM runs.segmentation_to_flow STF2 WHERE STF2.key_flow = STF.key_flow)
        FROM runs.segmentation_to_flow STF INNER JOIN
             runs.flows F ON STF.key_flow = F.id_flow
        WHERE STF.key_segmentation = _key_segmentation;

    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION runs.flows_of_segmentation(HSTORE) TO atum_user;
