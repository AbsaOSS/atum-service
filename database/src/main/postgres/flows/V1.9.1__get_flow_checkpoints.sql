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

CREATE OR REPLACE FUNCTION flows.get_flow_checkpoints(
    IN i_partitioning_of_flow JSONB,
    IN i_limit                INT DEFAULT 5,
    IN i_checkpoint_name      TEXT DEFAULT NULL,
    OUT status                INTEGER,
    OUT status_text           TEXT,
    OUT id_checkpoint         UUID,
    OUT checkpoint_name       TEXT,
    OUT measure_name          TEXT,
    OUT measure_columns       TEXT[],
    OUT measurement_value     JSONB,
    OUT checkpoint_start_time TIMESTAMP WITH TIME ZONE,
    OUT checkpoint_end_time   TIMESTAMP WITH TIME ZONE
) RETURNS SETOF record AS
$$
-------------------------------------------------------------------------------
--
-- Function: flows.get_flow_checkpoints(3)
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
--      i_limit                 - (optional) maximum number of data returned
--                                if 0 specified, all data will be returned, i.e. no limit will be applied
--      i_checkpoint_name       - (optional) if specified, returns data related to particular checkpoint's name
--
--      Note: checkpoint name uniqueness is not enforced by the data model, so there can be multiple different
--          checkpoints with the same name in the DB, i.e. multiple checkpoints can be retrieved even when
--          specifying `i_checkpoint_name` parameter
--
-- Returns:
--      status              - Status code
--      status_text         - Status text
--      id_checkpoint       - id of retrieved checkpoint
--      checkpoint_name     - name of retrieved checkpoint
--      measure_name        - measure name associated with a given checkpoint
--      measure_columns     - measure columns associated with a given checkpoint
--      measurement_value   - measurement details associated with a given checkpoint
--      checkpoint_time     - time
--
-- Status codes:
--      11                  - OK
--      41                  - Partitioning not found
--
-------------------------------------------------------------------------------

DECLARE
    _fk_partitioning BIGINT;
    _fk_flow BIGINT;
BEGIN
    _fk_partitioning = runs._get_id_partitioning(i_partitioning_of_flow);

    IF _fk_partitioning IS NULL THEN
        status := 41;
        status_text := 'Partitioning not found';
        RETURN NEXT;
        RETURN;
    END IF;

    SELECT id_flow
    FROM flows.flows
    WHERE fk_primary_partitioning = _fk_partitioning
    INTO _fk_flow;

    RETURN QUERY
    SELECT 11 AS status, 'OK' AS status_text,
           CP.id_checkpoint, CP.checkpoint_name,
           MD.measure_name, MD.measured_columns,
           M.measurement_value,
           CP.process_start_time AS checkpoint_start_time, CP.process_end_time AS checkpoint_end_time
    FROM flows.partitioning_to_flow AS PF
    JOIN runs.checkpoints AS CP
      ON PF.fk_partitioning = CP.fk_partitioning
    JOIN runs.measurements AS M
      ON CP.id_checkpoint = M.fk_checkpoint
    JOIN runs.measure_definitions AS MD
      ON M.fk_measure_definition = MD.id_measure_definition
    WHERE PF.fk_flow = _fk_flow
      AND (i_checkpoint_name IS NULL OR CP.checkpoint_name = i_checkpoint_name)
    ORDER BY CP.process_start_time,
             CP.id_checkpoint
    LIMIT nullif(i_limit, 0); -- NULL means no limit will be applied, it's same as LIMIT ALL

END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

GRANT EXECUTE ON FUNCTION flows.get_flow_checkpoints(JSONB, INT, TEXT) TO atum_owner;
