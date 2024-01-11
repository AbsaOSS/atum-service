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
    IN  i_partitioning      JSONB,
    IN  i_additional_data   HSTORE,
    IN  i_by_user           TEXT,
    OUT status              INTEGER,
    OUT status_text         TEXT,
    OUT id_additional_data  BIGINT
) RETURNS record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs.write_additional_data(3)
--      Adds the additional data for the input partitioning. If additional data of the given name already
--      exists for such partitioning, the value is updated and the old value is moved to the
--      additional data history table.
--
-- Parameters:
--      i_partitioning      - partitioning to add the additional data for
--      i_additional_data   - sets of key/value pairs representing name and values of the additional data
--      i_by_user           - user behind the change
--
-- Returns:
--      status              - Status code
--      status_text         - Status text
--      id_additional_data  - id of the data added
--
-- Status codes:
--      11                  - Additional data have been added
--      12                  - Additional data have been upserted
--		14					- Additional data already exist
--      41                  - Partitioning not found
--
-------------------------------------------------------------------------------
DECLARE
    _fk_partitioning BIGINT;
    _ad_backup_performed   BOOLEAN;
BEGIN

    _fk_partitioning := runs._get_id_partitioning(i_partitioning, true);

    IF _fk_partitioning IS NULL THEN
        status := 41;
        status_text := 'Partitioning not found';
        RETURN;
    END IF;

    -- 1. (backup) get records that already exist but values differ, and insert them into ad history table
    -- 2. (update) get records that already exist but values differ, and update the ad table with new values
    -- 3. (insert) get records that do not not exist yet and insert it into ad table
    --    (their original rows were previously saved in step 1)
    INSERT INTO runs.additional_data_history
        (fk_partitioning, ad_name, ad_value, created_by_originally, created_at_originally, archived_by)
    SELECT ad_curr.fk_partitioning, ad_curr.ad_name, ad_curr.ad_value,
           ad_curr.created_by, ad_curr.created_at, i_by_user
    FROM runs.additional_data AS ad_curr
    WHERE ad_curr.fk_partitioning = _fk_partitioning
      AND EXISTS (  -- get only those records where keys exist but values differ - so will be backed-up and later updated
          SELECT *
          FROM each(i_additional_data) AS ad_input(ad_key, ad_value)
          WHERE ad_curr.ad_name = ad_input.ad_key
            AND ad_curr.ad_value != ad_input.ad_value
      );

    IF found THEN
        UPDATE runs.additional_data AS ad_curr
        SET ad_value = ad_input.ad_value,
            created_by = i_by_user,
            created_at = now()
        FROM (
            SELECT ad_key, ad_value
            FROM each(i_additional_data) AS ad_input(ad_key, ad_value)
        ) as ad_input
        WHERE ad_curr.fk_partitioning = _fk_partitioning
          AND ad_curr.ad_name = ad_input.ad_key
          AND ad_curr.ad_value != ad_input.ad_value;

        _ad_backup_performed := TRUE;
    ELSE
        _ad_backup_performed := FALSE;
    END IF;

    INSERT INTO runs.additional_data (fk_partitioning, ad_name, ad_value, created_by)
    SELECT _fk_partitioning, ad_input.key, ad_input.value, i_by_user
    FROM each(i_additional_data) AS ad_input
    ON CONFLICT (fk_partitioning, ad_name) DO NOTHING;

    IF _ad_backup_performed THEN
        status := 12;
        status_text := 'Additional data have been upserted';
    ELSE
        IF found THEN
            status := 11;
            status_text := 'Additional data have been added';
        ELSE
            status := 14;
            status_text := 'Additional data already exist';
        END IF;
    END IF;

    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

ALTER FUNCTION runs.write_additional_data(JSONB, HSTORE, TEXT) OWNER TO atum_owner;
GRANT EXECUTE ON FUNCTION runs.write_additional_data(JSONB, HSTORE, TEXT) TO atum_user;
