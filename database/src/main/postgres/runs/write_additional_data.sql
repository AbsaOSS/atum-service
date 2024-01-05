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
-- Status codes:
--      11                  - Additional data have been added
--      12                  - Additional data have been upserted
--		14					- Additional data already exist
--      41                  - Partitioning not found
--      60                  - Additional data value cannot be NULL
--
-------------------------------------------------------------------------------
DECLARE
    _fk_partitioning BIGINT;
    _ad_backup_performed   BOOLEAN;
BEGIN

    PERFORM 1
    FROM (
        SELECT svals(i_additional_data) AS ad
    ) AS ad_keys
    WHERE ad_keys.ad IS NULL;

    IF found THEN
        status := 60;
        status_text := 'Additional data value cannot be NULL';
        RETURN;
    END IF;

    _fk_partitioning := runs._get_id_partitioning(i_partitioning);

    IF _fk_partitioning IS NULL THEN
        status := 41;
        status_text := 'Partitioning not found';
        RETURN;
    END IF;

    -- 1. (backup) get records that already exist and insert them into ad history table
    -- 2. (upsert) get records that do not not exist yet and insert it into ad table, and update existing records
    --    (their original rows were previously saved in step 1)
    WITH input_ad_expanded AS (
        SELECT e.key, e.value
        FROM each(i_additional_data) AS e
    ), ad_records_to_backup AS (
        SELECT fk_partitioning, ad_name, ad_value, created_by, created_at, i_by_user
        FROM runs.additional_data AS existing_ad
        JOIN input_ad_expanded AS input_ad
          ON input_ad.key = existing_ad.ad_name
        WHERE existing_ad.fk_partitioning = _fk_partitioning
          AND input_ad.value != existing_ad.ad_value
    )
    INSERT INTO runs.additional_data_history
        (fk_partitioning, ad_name, ad_value, created_by_originally, created_at_originally, archived_by)
    SELECT * FROM ad_records_to_backup;

    IF found THEN
        _ad_backup_performed := TRUE;
    ELSE
        _ad_backup_performed := FALSE;
    END IF;

    WITH input_ad_expanded AS (
        SELECT e.key, e.value
        FROM each(i_additional_data) AS e
    ), ad_records_to_update AS (
        SELECT _fk_partitioning, input_ad.key, input_ad.value, i_by_user
        FROM runs.additional_data AS existing_ad
        JOIN input_ad_expanded AS input_ad
          ON input_ad.key = existing_ad.ad_name
        WHERE existing_ad.fk_partitioning = _fk_partitioning
          AND input_ad.value != existing_ad.ad_value
    )
    INSERT INTO runs.additional_data (fk_partitioning, ad_name, ad_value, created_by)
    SELECT * FROM ad_records_to_update
    ON CONFLICT (fk_partitioning, ad_name) DO UPDATE
        SET ad_value = EXCLUDED.ad_value,
            created_by = i_by_user,
            created_at = now();

    WITH input_ad_expanded AS (
        SELECT _fk_partitioning, e.key, e.value, i_by_user
        FROM each(i_additional_data) AS e
    )
    INSERT INTO runs.additional_data (fk_partitioning, ad_name, ad_value, created_by)
    SELECT * FROM input_ad_expanded
    ON CONFLICT (fk_partitioning, ad_name) DO NOTHING;

    IF found THEN
        IF _ad_backup_performed THEN
            status := 12;
            status_text := 'Additional data have been upserted';
        ELSE
            status := 11;
            status_text := 'Additional data have been added';
        END IF;
    ELSE
        status := 14;
        status_text := 'Additional data already exist';
    END IF;

    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

ALTER FUNCTION runs.write_additional_data(JSONB, HSTORE, TEXT) OWNER TO atum_owner;
GRANT EXECUTE ON FUNCTION runs.write_additional_data(JSONB, HSTORE, TEXT) TO atum_user;
