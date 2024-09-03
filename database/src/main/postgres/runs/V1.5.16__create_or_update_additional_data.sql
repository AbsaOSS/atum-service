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

CREATE OR REPLACE FUNCTION runs.create_or_update_additional_data(
    IN i_partitioning_id BIGINT,
    IN i_additional_data HSTORE,
    IN i_by_user TEXT,
    OUT status INTEGER,
    OUT status_text TEXT,
    OUT o_ad_name TEXT,
    OUT o_ad_value TEXT,
    OUT o_ad_author TEXT
) RETURNS SETOF record AS
$$
    -------------------------------------------------------------------------------
--
-- Function: runs.create_or_update_additional_data(3)
--      Adds the additional data for the input partitioning. If additional data of a given name already
--      exists for such partitioning, the value is updated and the old value is moved to the
--      additional data history table.
--      The function returns all actual additional data of the partitioning.
--
-- Parameters:
--      i_partitioning_id   - id of partitioning to add the additional data for
--      i_additional_data   - sets of key/value pairs representing name and values of the additional data
--      i_by_user           - user behind the change (an author of AD records if there will be something to upsert)
--
-- Returns:
--      status              - Status code
--      status_text         - Status text
--      ad_name             - Name of the additional data
--      ad_value            - Value of the additional data
--      ad_author           - Author of the additional data
--
-- Status codes:
--      11                  - Additional data have been updated, added or both
--      14                  - No changes in additional data (this is when they already existed)
--      41                  - Partitioning not found
--
-------------------------------------------------------------------------------
DECLARE
    _records_updated BOOLEAN;
BEGIN

    PERFORM 1 FROM runs.partitionings WHERE id_partitioning = i_partitioning_id;
    IF NOT FOUND THEN
        status := 41;
        status_text := 'Partitioning not found';
        RETURN NEXT;
        RETURN;
    END IF;

    -- 1. (backup) get records that already exist but values differ,
    --             then insert them into AD history table and
    --             then update the actual AD table with new values
    _records_updated := runs._update_existing_additional_data(i_partitioning_id, i_additional_data, i_by_user);

    -- 2. (insert) get records that do not not exist yet and insert it into ad table
    --    (their original rows were previously saved in step 1)
    INSERT INTO runs.additional_data (fk_partitioning, ad_name, ad_value, created_by)
    SELECT i_partitioning_id, ad_input.key, ad_input.value, i_by_user
    FROM each(i_additional_data) AS ad_input
    ON CONFLICT (fk_partitioning, ad_name) DO NOTHING;

    -- 3. return the updated additional data (all, not only updated/added records)
    IF not _records_updated AND not found THEN
        RETURN QUERY
            SELECT 14, 'No changes in additional data', GPAD.ad_name, GPAD.ad_value, GPAD.ad_author
            FROM runs.get_partitioning_additional_data(i_partitioning_id) AS GPAD;
        RETURN;
    ELSE
        RETURN QUERY
            SELECT 11, 'Additional data have been updated, added or both', GPAD.ad_name, GPAD.ad_value, GPAD.ad_author
            FROM runs.get_partitioning_additional_data(i_partitioning_id) AS GPAD;
        RETURN;
    END IF;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

ALTER FUNCTION runs.create_or_update_additional_data(BIGINT, HSTORE, TEXT) OWNER TO atum_owner;
GRANT EXECUTE ON FUNCTION runs.create_or_update_additional_data(BIGINT, HSTORE, TEXT) TO atum_user;
