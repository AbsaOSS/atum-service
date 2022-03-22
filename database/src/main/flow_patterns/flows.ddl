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

-- DROP TABLE IF EXISTS flow_patterns.flows;

CREATE TABLE flow_patterns.flows
(
    id_fp_flow          BIGINT NOT NULL DEFAULT global_id(),
    flow_name           TEXT NOT NULL,
    flow_description    TEXT,
    created_by          TEXT NOT NULL,
    created_when        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT fp_flow_pk PRIMARY KEY (id_fp_flow)
);

ALTER TABLE flow_patterns.flows
    ADD CONSTRAINT fp_flows_unq UNIQUE (flow_name);

ALTER TABLE flow_patterns.flows OWNER to atum_owner;
