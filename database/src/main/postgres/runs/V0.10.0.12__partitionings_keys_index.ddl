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

-- Supports exact key/value subset searches without depending on the stored keys array order.
--
-- Built CONCURRENTLY so writes to runs.partitionings are not blocked. This file must contain only this
-- statement: Flyway then runs it outside of a transaction, which CONCURRENTLY requires.
-- If the build fails, it leaves an INVALID index behind; drop it
-- (DROP INDEX CONCURRENTLY runs.partitionings_keys_to_values_idx) and re-run the migration.
CREATE INDEX CONCURRENTLY partitionings_keys_to_values_idx ON runs.partitionings
    USING gin ((partitioning -> 'keysToValuesMap') jsonb_path_ops);
