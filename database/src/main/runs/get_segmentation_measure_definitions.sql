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

CREATE OR REPLACE FUNCTION runs.get_segmentation_measure_definitions(
    IN  i_segmentation                          HSTORE,
    OUT status                                  INTEGER,
    OUT status_text                             TEXT,
    OUT id_checkpoint_measure_definition        BIGINT,
    OUT measure_type                            TEXT,
    OUT measure_fields                          TEXT[],
    OUT created_by                              TEXT,
    OUT created_at                              TIMESTAMP WITH TIME ZONE
) RETURNS SETOF record AS
 $$
 -------------------------------------------------------------------------------
 --
 -- Function: runs.get_segmentation_measure_definitions(1)
 --      Return's all measure definitions, that are associated with the given segmentation.
 --      If the given segmentation does not exist, exactly one record is returned with the error status.
 --
 -- Parameters:
 --      i_segmentation      - segmentation for which the data are bound to
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
     _key_segmentation   BIGINT;
 BEGIN
     _key_segmentation = runs._get_key_segmentation(i_segmentation);

     IF _key_segmentation IS NULL THEN
         status := 41;
         status_text := 'Segmentation not found';
         RETURN NEXT;
         RETURN;
     END IF;

     RETURN QUERY
     SELECT 10,
            'OK',
            CMD.id_checkpoint_measure_definition,
            CMD.measure_type,
            CMD.measure_fields,
            CMD.created_by,
            CMD.created_at
     FROM runs.checkpoint_measure_definitions CMD
     WHERE CMD.key_segmentation = _key_segmentation;

     RETURN;
 END;
 $$
 LANGUAGE plpgsql VOLATILE SECURITY DEFINER;

 GRANT EXECUTE ON FUNCTION runs.get_segmentation_measure_definitions(HSTORE) TO atum_user;
