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

CREATE OR REPLACE FUNCTION runs.add_parent(
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
-- Function: runs.add_parent(5)
--      Add the Parent Partition for a given partition ID and copies measurements and additional data from parent.
--
-- Parameters:
--      i_id_partitioning                - id of the partition to be changed
--      i_id_parent_partitioning         - id of the new parent of the partition,
--      i_by_user                        - user behind the change
--      i_copy_measurements              - copies measurements
--      i_copy_additional_data           - copies additional data
--
-- Returns:
--      status              - Status code
--      status_text         - Status message
--
-- Status codes:
--      11 - OK
--      41 - Child Partitioning not found
--      42 - Parent Partitioning not found
--
-------------------------------------------------------------------------------
DECLARE
    Additional_Data HSTORE;

BEGIN

    PERFORM 1 FROM runs.partitionings WHERE id_partitioning = i_id_partitioning;
    IF NOT FOUND THEN
        status := 41;
        status_text := 'Child Partitioning not found';
        RETURN;
    END IF;

    PERFORM 1 FROM runs.partitionings WHERE id_partitioning = i_id_parent_partitioning;
    IF NOT FOUND THEN
        status := 42;
        status_text := 'Parent Partitioning not found';
        RETURN;
    END IF;

--    SELECT F.id_flow as mainFlow
--    FROM runs.get_partitioning_main_flow(i_partitioning_id) AS F
--    INTO mainFlow;

--    flow_id := array(
--            SELECT fk_flow AS flow_id
--            FROM flows.partitioning_to_flow
--            WHERE fk_partitioning = i_partitioning_id
--            AND fk_flow != mainFlow
--    );

--    FOREACH var IN ARRAY flow_id LOOP
--        DELETE FROM flows.partitioning_to_flow AS PTF
--        WHERE PTF.fk_partitioning = i_partitioning_id
--        AND PTF.fk_flow = var;
--    END LOOP;

    IF i_copy_additional_data THEN
        SELECT
            hstore(array_agg(PAD.ad_name), array_agg(PAD.ad_value)) AS Additional_Data
        FROM
            runs.get_partitioning_additional_data(i_id_parent_partitioning) AS PAD
        INTO
            Additional_Data;
        PERFORM 1 FROM runs.create_or_update_additional_data(i_id_partitioning, Additional_Data, i_by_user);
    END IF;

    IF i_copy_measurements THEN
        INSERT INTO runs.measure_definitions (fk_partitioning, measure_name, measured_columns, created_by)
        SELECT i_id_partitioning, PMI.measure_name, PMI.measured_columns, i_by_user
            FROM
                runs.get_partitioning_measures_by_id(i_id_parent_partitioning) AS PMI;
    END IF;

    PERFORM 1 FROM flows._add_to_parent_flows(i_id_parent_partitioning, i_id_partitioning, i_by_user);
    status := 11;
    status_text := 'Parent Updated';
    RETURN;

END;
$$
    LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

ALTER FUNCTION runs.add_parent(BIGINT, BIGINT, TEXT, BOOLEAN, BOOLEAN) OWNER TO atum_owner;
GRANT EXECUTE ON FUNCTION runs.add_parent(BIGINT, BIGINT, TEXT, BOOLEAN, BOOLEAN) TO atum_user;
