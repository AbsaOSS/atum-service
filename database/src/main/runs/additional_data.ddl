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

-- DROP TABLE IF EXISTS runs.additional_data;

CREATE TABLE runs.additional_data
(
    id_additional_data      BIGINT NOT NULL DEFAULT global_id(),
    key_segmentation        BIGINT NOT NULL,
    ad_name                 TEXT NOT NULL,
    ad_value                TEXT,
    created_by              TEXT NOT NULL,
    created_when            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_by              TEXT NOT NULL,
    updated_when            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT additional_data_pk PRIMARY KEY (id_additional_data)
);

ALTER TABLE runs.additional_data
    ADD CONSTRAINT flows_unq UNIQUE (key_segmentation, ad_name);

ALTER TABLE runs.additional_data OWNER to atum_owner;
