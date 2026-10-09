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

-- Supports the process start time window of runs.get_partitioning_checkpoints and flows.get_flow_checkpoints:
-- checkpoints of each partitioning are range-scanned by process_start_time.
--
-- Built CONCURRENTLY so writes to runs.checkpoints are not blocked. This file must contain only this statement:
-- Flyway then runs it outside of a transaction, which CONCURRENTLY requires.
-- If the build fails, it leaves an INVALID index behind; drop it (DROP INDEX CONCURRENTLY runs.checkpoints_idx2)
-- and re-run the migration.
CREATE INDEX CONCURRENTLY checkpoints_idx2 ON runs.checkpoints (fk_partitioning, process_start_time);
