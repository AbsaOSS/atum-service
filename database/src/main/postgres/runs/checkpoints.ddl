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

CREATE TABLE runs.checkpoints
(
    id_checkpoint           UUID NOT NULL,
    fk_partitioning         BIGINT NOT NULL,
    checkpoint_name         TEXT NOT NULL,
    process_start_time      TIMESTAMP WITH TIME ZONE NOT NULL,
    process_end_time        TIMESTAMP WITH TIME ZONE,
    created_by              TEXT NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT segments_pk PRIMARY KEY (id_checkpoint)
);

COMMENT ON COLUMN runs.checkpoints.id_checkpoint is 'The UUID is coming from outside world to distinguish repeated entry from a re-run checkpoint';

CREATE INDEX checkpoints_idx1 ON runs.checkpoints (fk_partitioning);

ALTER TABLE runs.checkpoints OWNER to atum_owner;
