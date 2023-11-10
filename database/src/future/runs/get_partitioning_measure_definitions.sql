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

CREATE OR REPLACE FUNCTION runs.get_partitioning_measure_definitions(
    IN  i_partitioning                          JSONB,
    OUT status                                  INTEGER,
    OUT status_text                             TEXT,
    OUT id_measure_definition        BIGINT,
    OUT measure_type                            TEXT,
    OUT measure_fields                          TEXT[],
    OUT created_by                              TEXT,
    OUT created_at                              TIMESTAMP WITH TIME ZONE
) RETURNS SETOF record AS
 $$
 -------------------------------------------------------------------------------
 --
 -- Function: runs.get_partitioning_measure_definitions(1)
 --      Return's all measure definitions, that are associated with the given segmentation.
 --      If the given segmentation does not exist, exactly one record is returned with the error status.
 --
 -- Parameters:
 --      i_partitioning      - segmentation for which the data are bound to
 --
 -- Returns:
 --      measure_type        - Measure type
 --      measure_fields      - Measure fields
 --      created_by          - who created the entry
 --      created_at          - when was the entry created
 --
 -- Status codes:
 --      10                  - OK
 --      41                  - Segmentation not found
 --
 -------------------------------------------------------------------------------
 DECLARE
     _fk_partitioning    BIGINT;
 BEGIN
     _fk_partitioning = runs._get_key_segmentation(i_partitioning);

     IF _fk_partitioning IS NULL THEN
         status := 41;
         status_text := 'Segmentation not found';
         RETURN NEXT;
         RETURN;
     END IF;

     RETURN QUERY
     SELECT 10,
            'OK',
            CMD.id_measure_definition,
            CMD.measure_type,
            CMD.measure_fields,
            CMD.created_by,
            CMD.created_at
     FROM runs.measure_definitions CMD
     WHERE CMD.fk_partitioning = _fk_partitioning;

     RETURN;
 END;
 $$
 LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

ALTER FUNCTION runs.get_partitioning_measure_definitions(JSONB) OWNER TO atum_owner;
GRANT EXECUTE ON FUNCTION runs.get_partitioning_measure_definitions(JSONB) TO atum_user;
