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

CREATE OR REPLACE FUNCTION runs.update_partitioning_parent(
    IN i_id_partitioning          BIGINT,
    IN i_id_parent_partitioning   BIGINT,
    IN i_by_user                  TEXT,
    IN i_copy_measurements        BOOLEAN DEFAULT true,
    IN i_copy_additional_data     BOOLEAN DEFAULT true,
    OUT status                    INTEGER,
    OUT status_text               TEXT
) RETURNS record AS
$$
-------------------------------------------------------------------------------
--
-- Function: runs.update_partitioning_parent(5)
--      Update the Parent Partitioning for a given partitioning ID and copies measurements and additional data from parent.
--
--      The link is written by flows._link_to_parents, the routine shared with
--      runs.create_partitioning_with_parents: it rejects self-links and cycles, serializes writers and propagates the
--      child (with all of its descendants) into every flow containing the parent.
--
--      Order of work: validate (41, 42) -> link (32 on a cycle) -> copy. The link comes first because fa-db commits
--      the transaction before it inspects the status, so a non-OK status must be returned before the first write
--      (including the copies below).
--
--      The copies are applied according to the flags also when the link was already in place (the helper's
--      "nothing to do" outcome is not an error here), so a repeated call is harmless:
--      - additional data (P8-Q1: the flag is kept, opt-in on this endpoint only, default true): copied with the
--        unchanged semantics, i.e. the parent's keys OVERWRITE the child's existing keys with the same name;
--      - measure definitions: INSERT ... ON CONFLICT ON CONSTRAINT measure_definitions_unq DO NOTHING, so the child's
--        own definitions win and a repeat is a no-op (not a unique violation).
--
-- Parameters:
--      i_id_partitioning                - id of the partitioning to be changed
--      i_id_parent_partitioning         - id of the new parent of the partitioning,
--      i_copy_measurements              - copies measurements from parent partitioning to child
--      i_copy_additional_data           - copies additional data from parent partitioning to child
--      i_by_user                        - user behind the change
--
-- Returns:
--      status              - Status code
--      status_text         - Status message
--
-- Status codes:
--      11 - OK
--      32 - The parent is the child itself or one of its descendants (the link would create a cycle);
--           the status text names the offending partitioning id(s); nothing is written
--      41 - Child Partitioning not found
--      42 - Parent Partitioning not found
--
-------------------------------------------------------------------------------
DECLARE
    _additional_data    HSTORE;
    _link_status        INTEGER;
    _link_status_text   TEXT;
BEGIN

    --Check for child partitioning
    PERFORM 1 FROM runs.partitionings WHERE id_partitioning = i_id_partitioning;
    IF NOT FOUND THEN
        status := 41;
        status_text := 'Child Partitioning not found';
        RETURN;
    END IF;

    --Check for parent partitioning
    PERFORM 1 FROM runs.partitionings WHERE id_partitioning = i_id_parent_partitioning;
    IF NOT FOUND THEN
        status := 42;
        status_text := 'Parent Partitioning not found';
        RETURN;
    END IF;

    --Link FIRST: a self-link or a cycle returns 32 before anything is written
    SELECT LTP.status, LTP.status_text
    FROM flows._link_to_parents(i_id_partitioning, ARRAY[i_id_parent_partitioning], i_by_user) AS LTP
    INTO _link_status, _link_status_text;
    IF _link_status = 32 THEN
        status := 32;
        status_text := _link_status_text;
        RETURN;
    END IF;

    --IF i_copy_additional_data is true,
    --THEN copy additional data of parent to child
    IF i_copy_additional_data THEN
        SELECT
            hstore(array_agg(PAD.ad_name), array_agg(PAD.ad_value)) AS Additional_Data
        FROM
            runs.get_partitioning_additional_data(i_id_parent_partitioning) AS PAD
        INTO
            _additional_data;
        PERFORM 1 FROM runs.create_or_update_additional_data(i_id_partitioning, _additional_data, i_by_user);
    END IF;

    --IF i_copy_measurements is true,
    --THEN copy measurements of parent to child (the child's own definitions win)
    IF i_copy_measurements THEN
        INSERT INTO runs.measure_definitions (fk_partitioning, measure_name, measured_columns, created_by)
        SELECT i_id_partitioning, PMI.measure_name, PMI.measured_columns, i_by_user
        FROM
            runs.get_partitioning_measures_by_id(i_id_parent_partitioning) AS PMI
        ON CONFLICT ON CONSTRAINT measure_definitions_unq DO NOTHING;
    END IF;

    status := 11;
    status_text := 'OK';
    RETURN;

END;
$$
    LANGUAGE plpgsql VOLATILE SECURITY DEFINER
    SET search_path = pg_catalog, public, pg_temp;

ALTER FUNCTION runs.update_partitioning_parent(BIGINT, BIGINT, TEXT, BOOLEAN, BOOLEAN) OWNER TO atum_owner;
GRANT EXECUTE ON FUNCTION runs.update_partitioning_parent(BIGINT, BIGINT, TEXT, BOOLEAN, BOOLEAN) TO atum_user;
