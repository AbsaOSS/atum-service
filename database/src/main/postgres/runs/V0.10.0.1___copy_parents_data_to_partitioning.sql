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

CREATE OR REPLACE FUNCTION runs._copy_parents_data_to_partitioning(
    IN  i_id_partitioning               BIGINT,
    IN  i_conflict_scope_parent_ids     BIGINT[],
    IN  i_copy_from_parent_ids          BIGINT[],
    IN  i_by_user                       TEXT,
    OUT status                          INTEGER,
    OUT status_text                     TEXT,
    OUT skipped_ad_keys                 TEXT[]
) RETURNS record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs._copy_parents_data_to_partitioning(4)
--      Copies measure definitions and additional data from parent partitionings to a child
--      partitioning following the multi-parent union + de-duplication policy:
--        * measure definitions - union across the copy-from parents, de-duplicated by
--          (measure_name, measured_columns); the earliest parent row's created_by/created_at is kept;
--          definitions the child already has are left untouched
--        * additional data - child-then-skip: the child's own keys always win; a key whose values
--          differ across the conflict-scope parents (NULL counts as a value) is skipped; a key with a
--          single value across those parents is copied once
--
-- Parameters:
--      i_id_partitioning               - id of the child partitioning receiving the data
--      i_conflict_scope_parent_ids     - all parents considered for additional data conflict detection
--      i_copy_from_parent_ids          - parents whose data is copied (the newly linked parents)
--      i_by_user                       - user behind the change
--
-- Returns:
--      status              - Status code
--      status_text         - Status text
--      skipped_ad_keys     - additional data keys skipped due to conflicting parent values, sorted, never NULL
--
-- Status codes:
--      11                  - Parent data copied
--      14                  - Nothing to copy
--
-------------------------------------------------------------------------------
DECLARE
    _copy_from              BIGINT[];
    _conflict_scope         BIGINT[];
BEGIN
    skipped_ad_keys := '{}'::TEXT[];

    _copy_from := ARRAY(
        SELECT DISTINCT PID.id
        FROM unnest(i_copy_from_parent_ids) AS PID(id)
        WHERE PID.id IS NOT NULL AND PID.id <> i_id_partitioning
    );

    IF cardinality(_copy_from) = 0 THEN
        status := 14;
        status_text := 'Nothing to copy';
        RETURN;
    END IF;

    _conflict_scope := ARRAY(
        SELECT DISTINCT PID.id
        FROM unnest(COALESCE(i_conflict_scope_parent_ids, '{}'::BIGINT[]) || _copy_from) AS PID(id)
        WHERE PID.id IS NOT NULL AND PID.id <> i_id_partitioning
    );

    -- measure definitions: union + de-duplication by (measure_name, measured_columns)
    INSERT INTO runs.measure_definitions (fk_partitioning, measure_name, measured_columns, created_by, created_at)
    SELECT DISTINCT ON (MD.measure_name, MD.measured_columns)
        i_id_partitioning, MD.measure_name, MD.measured_columns, MD.created_by, MD.created_at
    FROM runs.measure_definitions MD
    WHERE MD.fk_partitioning = ANY(_copy_from)
    ORDER BY MD.measure_name, MD.measured_columns, MD.created_at, MD.id_measure_definition
    ON CONFLICT ON CONSTRAINT measure_definitions_unq DO NOTHING;

    -- additional data: keys skipped because the parents disagree (reported only if the child lacks the key)
    SELECT COALESCE(array_agg(K.ad_name ORDER BY K.ad_name), '{}'::TEXT[])
    FROM (
        SELECT AD.ad_name
        FROM runs.additional_data AD
        WHERE AD.fk_partitioning = ANY(_conflict_scope)
        GROUP BY AD.ad_name
        HAVING cardinality(array_agg(DISTINCT AD.ad_value)) > 1
           AND bool_or(AD.fk_partitioning = ANY(_copy_from))
    ) AS K
    WHERE NOT EXISTS (
        SELECT 1
        FROM runs.additional_data CAD
        WHERE CAD.fk_partitioning = i_id_partitioning
          AND CAD.ad_name = K.ad_name
    )
    INTO skipped_ad_keys;

    -- additional data: copy single-valued keys; the child's own keys win
    INSERT INTO runs.additional_data (fk_partitioning, ad_name, ad_value, created_by)
    SELECT i_id_partitioning, AD.ad_name, (array_agg(DISTINCT AD.ad_value))[1], i_by_user
    FROM runs.additional_data AD
    WHERE AD.fk_partitioning = ANY(_conflict_scope)
    GROUP BY AD.ad_name
    HAVING cardinality(array_agg(DISTINCT AD.ad_value)) = 1
       AND bool_or(AD.fk_partitioning = ANY(_copy_from))
    ON CONFLICT ON CONSTRAINT ad_unq DO NOTHING;

    status := 11;
    status_text := 'Parent data copied';
    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

ALTER FUNCTION runs._copy_parents_data_to_partitioning(BIGINT, BIGINT[], BIGINT[], TEXT) OWNER TO atum_owner;
REVOKE EXECUTE ON FUNCTION runs._copy_parents_data_to_partitioning(BIGINT, BIGINT[], BIGINT[], TEXT) FROM PUBLIC;
