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

CREATE TABLE runs.checkpoint_properties
(
  id_checkpoint_property BIGINT NOT NULL DEFAULT global_id(),
  fk_checkpoint          UUID   NOT NULL,
  property_name          TEXT   NOT NULL,
  property_value         TEXT,
  CONSTRAINT checkpoint_properties_pk PRIMARY KEY (id_checkpoint_property),
  CONSTRAINT checkpoint_properties_fk_checkpoint FOREIGN KEY (fk_checkpoint)
    REFERENCES runs.checkpoints (id_checkpoint) ON DELETE CASCADE
);

CREATE INDEX idx_checkpoint_properties_name_value ON runs.checkpoint_properties (property_name, property_value);
ALTER TABLE runs.checkpoint_properties OWNER to atum_owner;
