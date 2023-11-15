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

CREATE OR REPLACE FUNCTION runs.get_partitioning_additional_data(
    IN  i_partitioning      JSONB,
    OUT status              INTEGER,
    OUT status_text         TEXT,
    OUT ad_name             TEXT,
    OUT ad_value            TEXT,
    OUT created_by          TEXT,
    OUT created_at          TIMESTAMP WITH TIME ZONE,
    OUT updated_by          TEXT,
    OUT updated_at          TIMESTAMP WITH TIME ZONE
) RETURNS SETOF record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs.get_partitioning_additional_data(1)
--      Return's all additional data, that are associated with the give segmentation.
--      If the given segmentation does not exist, exactly one record is returned with the error status.
--
-- Parameters:
--      i_partitioning      - segmentation for which the data are bound to
--
-- Returns:
--      status              - Status code
--      status_text         - Status text
--      ad_name             - name of the additional data entry
--      ad_value            - value of the additional data entry
--      created_by          - who created the entry
--      created_at          - when was the entry created
--      updated_by          - who lsat updated the entry
--      updated_at          - when was the entry last updated
--
-- Status codes:
--      10                  - OK
--      41                  - Segmentation not found
--
-------------------------------------------------------------------------------
DECLARE
    _fk_partitioning    BIGINT;
BEGIN
    _fk_partitioning = runs._get_id_partitioning(i_partitioning);

    IF _fk_partitioning IS NULL THEN
        status := 41;
        status_text := 'Partitioning not found';
        RETURN NEXT;
        RETURN;
    END IF;

    RETURN QUERY
    SELECT 10, 'OK', AD.ad_name, AD.ad_value,
           AD.created_by, AD.created_at, AD.updated_by, AD.updated_at
    FROM runs.additional_data AD
    WHERE AD.fk_partitioning = _fk_partitioning;

    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

ALTER FUNCTION runs.get_partitioning_additional_data(JSONB) OWNER TO atum_owner;
GRANT EXECUTE ON FUNCTION runs.get_partitioning_additional_data(JSONB) TO atum_user;
