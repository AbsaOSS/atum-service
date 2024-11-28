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

CREATE OR REPLACE FUNCTION runs.patch_partitioning_parent(
    IN i_partitioning_id    BIGINT,
    IN i_parent_id          BIGINT,
    IN i_by_user            TEXT,
    OUT status              INTEGER,
    OUT status_text         TEXT,
    OUT parent_id           BIGINT
) RETURNS record AS
$$
    -------------------------------------------------------------------------------
--
-- Function: runs.patch_partitioning_parent(1, 2, user_1)
--      Returns Parents' partition ID for the given id.
--      Updates the Parent Partition for a given partition ID.

--
-- Parameters:
--      i_partitioning_id   - id of the partition to be changed
--      i_parent_id         - id of the new parent of the partition,
--      i_by_user           - user behind the change
--
-- Returns:
--      status              - Status code
--      status_text         - Status message
--      parent_id           - ID of Parent partition

-- Status codes:
--      11 - OK
--      41 - Partitioning not found
--
-------------------------------------------------------------------------------
DECLARE
    flow_id     BIGINT[];
    mainFlow    BIGINT;
    var         BIGINT;

BEGIN

    PERFORM 1 FROM runs.partitionings WHERE id_partitioning = i_partitioning_id;
    IF NOT FOUND THEN
        status := 41;
        status_text := 'Child Partitioning not found';
        RETURN;
    END IF;

    PERFORM 1 FROM runs.partitionings WHERE id_partitioning = i_parent_id;
    IF NOT FOUND THEN
        status := 41;
        status_text := 'Parent Partitioning not found';
        RETURN;
    END IF;

    SELECT F.id_flow as mainFlow
    FROM runs.get_partitioning_main_flow(i_partitioning_id) AS F
    INTO mainFlow;

    flow_id := array(
            SELECT fk_flow AS flow_id
            FROM flows.partitioning_to_flow
            WHERE fk_partitioning = i_partitioning_id
            AND fk_flow != mainFlow
    );

    FOREACH var IN ARRAY flow_id LOOP
        DELETE FROM flows.partitioning_to_flow AS PTF
        WHERE PTF.fk_partitioning = i_partitioning_id
        AND PTF.fk_flow = var;
    END LOOP;

    PERFORM 1 FROM flows._add_to_parent_flows(i_parent_id, i_partitioning_id, i_by_user);
    status := 11;
    status_text := 'Parent Updated';
    parent_id := i_parent_id;

    RETURN;

END;
$$
    LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

ALTER FUNCTION runs.patch_partitioning_parent(BIGINT, BIGINT, TEXT) OWNER TO atum_owner;
GRANT EXECUTE ON FUNCTION runs.patch_partitioning_parent(BIGINT, BIGINT, TEXT) TO atum_user;
