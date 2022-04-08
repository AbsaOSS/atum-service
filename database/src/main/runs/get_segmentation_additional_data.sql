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

CREATE OR REPLACE FUNCTION runs.get_segmentation_additional_data(
    IN  i_segmentation      HSTORE,
    OUT status              INTEGER,
    OUT status_text         TEXT,
    OUT ad_anme             TEXT,
    OUT ad_value            TEXT,
    OUT created_by          TEXT,
    OUT created_at          TIMESTAMP WITH TIME ZONE,
    OUT created_by          TEXT,
    OUT created_at          TIMESTAMP WITH TIME ZONE,
) RETURNS record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs.get_segmentation_additional_data(1)
--      Return's all additional data, that are associated with the give segmentation.
--      If the given segmentation does not exist, exactly one record is returned with the error status.
--
-- Parameters:
--      i_parameter         -
--
-- Returns:
--      status              - Status code
--      status_text         - Status text
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
    SELECT 10, 'OK', AD.ad_name, AD.ad_value,
           AD.created_by, AD.created_at, AD.updated_by, AD.updated_at
    FROM runs.additional_data AD
    WHERE AD.key_segmentation = _key_segmentation;

    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION runs.get_segmentation_additional_data() TO [user];
