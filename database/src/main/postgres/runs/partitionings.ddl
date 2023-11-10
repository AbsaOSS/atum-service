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

CREATE TABLE runs.partitionings
(
    id_partitioning         BIGINT NOT NULL DEFAULT global_id(),
    partitioning            JSONB NOT NULL, -- TODO add  partitioning validity check #69
    created_by              TEXT NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT segmentations_pk PRIMARY KEY (id_partitioning)
);

ALTER TABLE runs.partitionings
    ADD CONSTRAINT partitioning_unq UNIQUE (partitioning);

ALTER TABLE runs.partitionings OWNER to atum_owner;
