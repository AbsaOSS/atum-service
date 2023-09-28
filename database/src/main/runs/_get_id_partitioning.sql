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

CREATE OR REPLACE FUNCTION runs._get_id_partitioning(
    IN  i_partitioning      JSONB,
    OUT id_partitioning     BIGINT
) RETURNS BIGINT AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs._get_key_segmentation(1)
--      Gets the id of the provided segmentation, if it exits
--
-- Parameters:
--      i_partitioning      - partitioning to look for
--
-- Returns:
--      id_partitioning     - id of the segmentation if it exists, NULL otherwise
--
-------------------------------------------------------------------------------
DECLARE
BEGIN
    SELECT PAR.id_partitioning
    FROM runs.partitionings PAR
    WHERE PAR.partitioning = i_partitioning
    INTO id_partitioning;

    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

ALTER FUNCTION runs._get_id_partitioning(JSONB) OWNER TO atum_owner;
