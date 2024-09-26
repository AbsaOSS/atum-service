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

-- Function: flows.get_flow_checkpoints_v2(4)
CREATE OR REPLACE FUNCTION flows.get_flow_checkpoints_v2(
    IN  i_flow_id              BIGINT,
    IN  i_limit                INT DEFAULT 5,
    IN  i_checkpoint_name      TEXT DEFAULT NULL,
    IN  i_offset               BIGINT DEFAULT 0,
    OUT status                 INTEGER,
    OUT status_text            TEXT,
    OUT id_checkpoint          UUID,
    OUT checkpoint_name        TEXT,
    OUT author                 TEXT,
    OUT measured_by_atum_agent BOOLEAN,
    OUT measure_name           TEXT,
    OUT measured_columns       TEXT[],
    OUT measurement_value      JSONB,
    OUT checkpoint_start_time  TIMESTAMP WITH TIME ZONE,
    OUT checkpoint_end_time    TIMESTAMP WITH TIME ZONE,
    OUT has_more               BOOLEAN
) RETURNS SETOF record AS
$$
--------------------------------------------------------------------------------------------------------------------
--
-- Function: flows.get_flow_checkpoints_v2(4)
--      Retrieves all checkpoints (measures and their measurement details) related to a primary flow
--      associated with the input partitioning.
--
-- Note: a single row returned from this function doesn't contain all data related to a single checkpoint - it only
--     represents one measure associated with a checkpoint. So even if only a single checkpoint would be retrieved,
--     this function can potentially return multiple rows.
--
-- Note: checkpoints will be retrieved in ordered fashion, by checkpoint_time and id_checkpoint
--
-- Parameters:
--      i_partitioning_of_flow  - partitioning to use for identifying the flow associate with checkpoints
--                                that will be retrieved
--      i_limit                 - (optional) maximum number of checkpoint's measurements to return
--                                if 0 specified, all data will be returned, i.e. no limit will be applied
--      i_checkpoint_name       - (optional) if specified, returns data related to particular checkpoint's name
--      i_offset                - (optional) offset for pagination
--
--      Note: checkpoint name uniqueness is not enforced by the data model, so there can be multiple different
--          checkpoints with the same name in the DB, i.e. multiple checkpoints can be retrieved even when
--          specifying `i_checkpoint_name` parameter
--
-- Returns:
--      status                 - Status code
--      status_text            - Status text
--      id_checkpoint          - ID of retrieved checkpoint
--      checkpoint_name        - Name of the retrieved checkpoint
--      author                 - Author of the checkpoint
--      measured_by_atum_agent - Flag indicating whether the checkpoint was measured by Atum Agent
--                               (if false, data supplied manually)
--      measure_name           - measure name associated with a given checkpoint
--      measured_columns       - measure columns associated with a given checkpoint
--      measurement_value      - measurement details associated with a given checkpoint
--      checkpoint_time        - time
--      has_more               - flag indicating whether there are more checkpoints available
--
-- Status codes:
--      11                     - OK
--      42                     - Flow not found
---------------------------------------------------------------------------------------------------
DECLARE
    _actual_limit INT := i_limit + 1;
    _record_count INT;
BEGIN
    -- Execute the query to retrieve checkpoints flow and their associated measurements
    RETURN QUERY
        SELECT 11 AS status,
               'OK' AS status_text,
               CP.id_checkpoint,
               CP.checkpoint_name,
               CP.created_by AS author,
               CP.measured_by_atum_agent,
               MD.measure_name,
               MD.measured_columns,
               M.measurement_value,
               CP.process_start_time AS checkpoint_start_time,
               CP.process_end_time AS checkpoint_end_time,
               (ROW_NUMBER() OVER ()) <= i_limit AS has_more
        FROM flows.partitioning_to_flow AS PF
                 JOIN runs.checkpoints AS CP
                      ON PF.fk_partitioning = CP.fk_partitioning
                 JOIN runs.measurements AS M
                      ON CP.id_checkpoint = M.fk_checkpoint
                 JOIN runs.measure_definitions AS MD
                      ON M.fk_measure_definition = MD.id_measure_definition
        WHERE PF.fk_flow = i_flow_id
          AND (i_checkpoint_name IS NULL OR CP.checkpoint_name = i_checkpoint_name)
        ORDER BY CP.process_start_time,
                 CP.id_checkpoint
        LIMIT _actual_limit
        OFFSET i_offset;

    GET DIAGNOSTICS _record_count = ROW_COUNT;

    IF _record_count > i_limit THEN
        has_more := TRUE;
    ELSE
        has_more := FALSE;
    END IF;

    IF NOT FOUND THEN
        status := 42;
        status_text := 'Flow not found';
        RETURN NEXT;
        RETURN;
    END IF;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION flows.get_flow_checkpoints_v2(BIGINT, INT, TEXT, BIGINT) TO atum_owner;
