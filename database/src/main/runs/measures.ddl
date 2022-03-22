/*
 * Copyright 2018 ABSA Group Limited
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

-- DROP TABLE IF EXISTS runs.measures;

CREATE TABLE runs.measures
(
    id_measure                          UUID NOT NULL,
    key_checkpoint_measure_definition   BIGINT NOT NULL,
    key_checkpoint                      UUID NOT NULL,
    value                               TEXT,
    value_whole_number                  BIGINT,
    CONSTRAINT measures_pk PRIMARY KEY (id_measure)
);

ALTER TABLE runs.measures
    ADD CONSTRAINT measures_unq UNIQUE (key_checkpoint, key_checkpoint_measure_definition);

CREATE INDEX measures_idx1 ON runs.measures (key_checkpoint_measure_definition);

ALTER TABLE runs.measures OWNER to atum_owner;
