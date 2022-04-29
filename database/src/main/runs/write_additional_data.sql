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

CREATE OR REPLACE FUNCTION runs.write_additional_data(
    IN  i_segmentation      HSTORE,
    IN  i_ad_name           TEXT,
    IN  i_ad_value          TEXT,
    IN  i_by_user           TEXT,
    OUT status              INTEGER,
    OUT status_text         TEXT,
    OUT id_additional_data  BIGINT
) RETURNS record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs.write_additional_data(4)
--      Adds the additional data for the segmentation. If additional data of the given name already exists for
--      the segmentation, the value is updated.
--
-- Parameters:
--      i_segmentation      - segmentation to add the additional data for
--      i_ad_name           - name of the additional data entry
--      i_ad_value          - value of the additional data entry
--      i_by_user           - user behind the change
--
-- Returns:
--      status              - Status code
--      status_text         - Status text
--      id_additional_data  - id of the data added
-- Status codes:
--      11                  - Additional data has been added
--      12                  - Pattern based additional data updated
--      41                  - Segmentation not found
--      60                  - Additional data value cannot be NULL
--
-------------------------------------------------------------------------------
DECLARE
    _key_segmentation   BIGINT;
BEGIN

    IF i_ad_value IS NULL THEN
        status := 60;
        status_text := 'Additional data value cannot be NULL';
        RETURN;
    END IF;

    _key_segmentation := runs._get_key_segmentation(i_segmentation);

    IF _key_segmentation IS NULL THEN
        status := 41;
        status_text := 'Segmentation not found';
        RETURN;
    END IF;


    UPDATE runs.additional_data
    SET ad_value = i_ad_value,
        updated_by = i_by_user,
        updated_at = now()
    WHERE key_segmentation = _key_segmentation AND
          ad_name = i_ad_name AND
          ad_value IS NULL;

    IF found THEN
        status := 12;
        status_text := 'Pattern based additional data updated';
    ELSE
        INSERT INTO runs.additional_data (key_segmentation, ad_name, ad_value, created_by, updated_by)
        VALUES (_key_segmentation, i_ad_name, i_ad_value, i_by_user, i_by_user);

        status := 11;
        status_text := 'Additional data has been added';
    END IF;

    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION runs.write_additional_data(HSTORE, TEXT, TEXT, TEXT) TO atum_user;
