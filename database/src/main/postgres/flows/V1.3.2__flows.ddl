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

CREATE TABLE flows.flows
(
    id_flow                 BIGINT NOT NULL DEFAULT global_id(),
    flow_name               TEXT NOT NULL,
    flow_description        TEXT,
    from_pattern            BOOLEAN NOT NULL,
    created_by              TEXT NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT flows_pk PRIMARY KEY (id_flow)
);

ALTER TABLE flows.flows OWNER to atum_owner;
