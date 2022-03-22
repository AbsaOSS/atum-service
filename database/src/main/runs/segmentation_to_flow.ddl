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

-- DROP TABLE IF EXISTS runs.segmentation_to_flow;

CREATE TABLE runs.segmentation_to_flow
(
    key_flow                BIGINT NOT NULL,
    key_segmentation        BIGINT NOT NULL,
    created_by              TEXT NOT NULL,
    created_when            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT segmentation_to_flow_pk PRIMARY KEY (key_flow, key_segmentation)
);

ALTER TABLE runs.segmentation_to_flow OWNER to atum_owner;
