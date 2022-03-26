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

CREATE OR REPLACE FUNCTION runs._get_key_segmentation(
    IN  i_segmentation      HSTORE,
    OUT key_segmentation    BIGINT
) RETURNS BIGINT AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs._get_key_segmentation(1)
--      Gets the id of the provided segmentation, if it exits
--
-- Parameters:
--      i_segmentation      - segmentation to look for
--
-- Returns:
--      key_segmentation    - id of the segmentation if it exists, NULL otherwise
--
-------------------------------------------------------------------------------
DECLARE
BEGIN
    SELECT SEG.id_segmentation
    FROM runs.segmentations SEG
    WHERE SEG.segmentation = i_segmentation
    INTO key_segmentation;

    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION runs._get_key_segmentation(hstore) TO atum_user;
