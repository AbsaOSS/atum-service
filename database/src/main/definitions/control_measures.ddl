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

-- DROP TABLE IF EXISTS definitions.control_measures;

CREATE TABLE definitions.control_measures
(
    id_control_measures     BIGINT NOT NULL DEFAULT global_id(),
    key_flow_definition     BIGINT NOT NULL,
    control_measure_name    TEXT NOT NULL,
    control_measure_fields  TEXT[] NOT NULL,
    control_measures_type   TEXT NOT NULL,
    created_by              TEXT NOT NULL,
    created_when            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT segments_pk PRIMARY KEY (id_control_measures)
);

ALTER TABLE definitions.control_measures
    ADD CONSTRAINT flows_unq UNIQUE (key_flow_definition, control_measure_name);

ALTER TABLE definitions.control_measures OWNER to atum_owner;
