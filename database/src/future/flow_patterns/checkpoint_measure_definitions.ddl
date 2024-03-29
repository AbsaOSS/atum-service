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

-- DROP TABLE IF EXISTS flow_patterns.measure_definitions;

CREATE TABLE flow_patterns.measure_definitions
(
    id_fp_measure_definition BIGINT NOT NULL DEFAULT global_id(),
    key_fp_flow                         BIGINT NOT NULL,
    measure_type                        TEXT NOT NULL,
    measure_fields                      TEXT[] NOT NULL,
    created_by                          TEXT NOT NULL,
    created_at                          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_by                          TEXT NOT NULL,
    updated_at                          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT fp_checkpoint_definitions_pk PRIMARY KEY (id_fp_measure_definition)
);

ALTER TABLE flow_patterns.measure_definitions
    ADD CONSTRAINT fp_measure_definitions_unq UNIQUE (key_fp_flow, measure_type, measure_fields);

ALTER TABLE flow_patterns.measure_definitions OWNER to atum_owner;
