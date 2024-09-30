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

CREATE TABLE runs.measurements
(
    id_measurement                      BIGINT NOT NULL DEFAULT global_id(),
    fk_measure_definition               BIGINT NOT NULL,
    fk_checkpoint                       UUID NOT NULL,
    measurement_value                   JSONB NOT NULL,
    CONSTRAINT measurements_pk PRIMARY KEY (id_measurement)
);

ALTER TABLE runs.measurements
    ADD CONSTRAINT measurements_unq UNIQUE (fk_checkpoint, fk_measure_definition);

CREATE INDEX measurements_idx1 ON runs.measurements (fk_measure_definition);

ALTER TABLE runs.measurements OWNER to atum_owner;
