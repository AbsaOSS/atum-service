/*
 * Copyright 2021 ABSA Group Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

CREATE OR REPLACE FUNCTION runs.get_partitioning_additional_data(
    IN  i_partitioning_id       BIGINT,
    OUT status                  INTEGER,
    OUT status_text             TEXT,
    OUT ad_name                 TEXT,
    OUT ad_value                TEXT,
    OUT ad_author               TEXT
) RETURNS SETOF record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs.get_partitioning_additional_data(1)
--      Returns additional data for the given partitioning
--
-- Parameters:
--      i_partitioning_id   - id of the partitioning for requested additional data
--
-- Returns:
--      status              - Status code
--      status_text         - Status message
--      ad_name             - Name of the additional data
--      ad_value            - Value of the additional data
--      ad_author           - Author of the additional data
--
-- Status codes:
--      11 - OK
--      16 - No additional data found
--      41 - Partitioning not found
--
-------------------------------------------------------------------------------

BEGIN
    PERFORM 1 FROM runs.partitionings WHERE id_partitioning = i_partitioning_id;
    IF NOT FOUND THEN
        status := 41;
        status_text := 'Partitioning not found';
        RETURN NEXT;
        RETURN;
    END IF;

    status = 11;
    status_text = 'OK';

    RETURN QUERY
        SELECT status, status_text, ad.ad_name, ad.ad_value, ad.created_by
        FROM runs.additional_data AS ad
        WHERE ad.fk_partitioning = i_partitioning_id;

    IF NOT FOUND THEN
        status := 16;
        status_text := 'No additional data found';
        RETURN NEXT;
        RETURN;
    END IF;
END;
$$
    LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

ALTER FUNCTION runs.get_partitioning_additional_data(BIGINT) OWNER TO atum_owner;
GRANT EXECUTE ON FUNCTION runs.get_partitioning_additional_data(BIGINT) TO atum_user;
