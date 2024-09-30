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

CREATE TABLE runs.additional_data_history
(
    id_additional_data          BIGINT NOT NULL, -- will be provided from the current AD during backup
    fk_partitioning             BIGINT NOT NULL,
    ad_name                     TEXT NOT NULL,
    ad_value                    TEXT,
    created_by_originally       TEXT NOT NULL,
    created_at_originally       TIMESTAMP WITH TIME ZONE NOT NULL,
    archived_by                 TEXT NOT NULL,
    archived_at                 TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT additional_data_history_pk PRIMARY KEY (id_additional_data)
);

ALTER TABLE runs.additional_data_history OWNER to atum_owner;
