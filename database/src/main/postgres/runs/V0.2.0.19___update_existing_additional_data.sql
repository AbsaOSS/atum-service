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

CREATE OR REPLACE FUNCTION runs._update_existing_additional_data(
    IN  i_fk_partitioning     BIGINT,
    IN  i_additional_data     HSTORE,
    IN  i_by_user             TEXT,
    OUT records_updated       BOOLEAN
) RETURNS BOOLEAN AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs._update_existing_additional_data(3)
--      The aim of this function is to back up additional data records that already exists in the DB for the input
--      partitioning.
--
--      If additional data of a given name already exists, the row is deleted, the old value is moved
--      to the additional data history table, and then a new row is inserted with new values
--      into additional data table (especially creation details and UUID).
--
--      If additional data of a given name doesn't exist for such partitioning yet, it will be skipped - the
--      responsibility of this function is to only back up existing data that it changes.
--
-- Parameters:
--      i_fk_partitioning   - partitioning FK that refers to the given additional data that will be archived
--      i_additional_data   - sets of key/value pairs representing name and values of the additional data
--      i_by_user           - user behind the change (who requested to 'archive' the given AD records)
--
-- Returns:
--      records_updated     - TRUE if the update was performed, FALSE otherwise
--
-------------------------------------------------------------------------------
DECLARE
BEGIN

    -- 0. (delete) get records that already exist but values differ, and delete them from AD table
    WITH deleted_rows AS (
        DELETE FROM runs.additional_data AS ad_curr
        WHERE ad_curr.fk_partitioning = i_fk_partitioning
          AND EXISTS (  -- get only those records where keys exist but values differ - so will be backed-up
              SELECT *
              FROM each(i_additional_data) AS ad_input(ad_key, ad_value)
              WHERE ad_curr.ad_name = ad_input.ad_key
                AND ad_curr.ad_value IS DISTINCT FROM ad_input.ad_value
          )
        RETURNING ad_curr.id_additional_data, ad_curr.fk_partitioning,
                  ad_curr.ad_name, ad_curr.ad_value,
                  ad_curr.created_by, ad_curr.created_at
    ),
    -- 1. (backup) get records that already exist but values differ, and insert them into AD history table
    backed_up_rows AS (
        INSERT INTO runs.additional_data_history
            (id_additional_data, fk_partitioning, ad_name, ad_value, created_by_originally, created_at_originally, archived_by)
        SELECT del_r.id_additional_data, del_r.fk_partitioning,
               del_r.ad_name, del_r.ad_value,
               del_r.created_by, del_r.created_at, i_by_user
        FROM deleted_rows AS del_r
    )
    -- 2. (insert) get records that were deleted, and insert the AD table with new values (including new UUID / PK!)
    INSERT INTO runs.additional_data
        (fk_partitioning, ad_name, ad_value, created_by, created_at)
    SELECT del_r.fk_partitioning,
           del_r.ad_name, ad_input.ad_value,
           i_by_user, now()
    FROM deleted_rows AS del_r
    JOIN each(i_additional_data) AS ad_input(ad_key, ad_value)
      ON del_r.ad_name = ad_input.ad_key;

    IF found THEN
        records_updated := TRUE;
    ELSE
        records_updated := FALSE;
    END IF;

    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

ALTER FUNCTION runs._update_existing_additional_data(BIGINT, HSTORE, TEXT) OWNER TO atum_owner;
