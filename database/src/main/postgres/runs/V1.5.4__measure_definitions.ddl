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

CREATE TABLE runs.measure_definitions
(
    id_measure_definition               BIGINT NOT NULL DEFAULT global_id(),
    fk_partitioning                     BIGINT NOT NULL,
    measure_name                        TEXT NOT NULL,
    measured_columns                    TEXT[] NOT NULL,
    created_by                          TEXT NOT NULL,
    created_at                          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT measure_definitions_pk PRIMARY KEY (id_measure_definition)
);

ALTER TABLE runs.measure_definitions
    ADD CONSTRAINT measure_definitions_unq UNIQUE (fk_partitioning, measure_name, measured_columns);

ALTER TABLE runs.measure_definitions OWNER to atum_owner;
