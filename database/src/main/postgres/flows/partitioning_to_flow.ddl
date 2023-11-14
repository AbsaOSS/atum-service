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

CREATE TABLE flows.partitioning_to_flow
(
    fk_flow                 BIGINT NOT NULL,
    fk_partitioning         BIGINT NOT NULL,
    created_by              TEXT NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT partitioning_to_flow_pk PRIMARY KEY (fk_flow, fk_partitioning)
);

ALTER TABLE flows.partitioning_to_flow OWNER to atum_owner;
