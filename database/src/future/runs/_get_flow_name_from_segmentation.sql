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

-- TODO
CREATE OR REPLACE FUNCTION runs._get_flow_name_from_segmentation(
    IN  i_segmentation      HSTORE,
    OUT flow_name           TEXT
) RETURNS TEXT AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs._get_flow_name_from_segmentation(1)
--      Extracts the flow name key from the segmentation
--
-- Parameters:
--      i_segmentation      - the segmentation to extract
--
-- Returns:
--      flow_name           - the flow name segment or NULL if it's not present
--
-------------------------------------------------------------------------------
DECLARE
BEGIN
    flow_name := i_segmentation -> 'flow_name';
END;
$$
LANGUAGE plpgsql IMMUTABLE SECURITY DEFINER;
