/*
 * Copyright 2021 ABSA Group Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

-- The primary key (fk_flow, fk_partitioning) cannot serve lookups by fk_partitioning alone ("the flows that contain
-- partitioning X"). flows._link_to_parents does exactly that for every parent while holding the global advisory lock,
-- so without this index each link would scan the whole table and serialize all writers behind that scan.
-- It also serves the ancestors query.
--
-- Built CONCURRENTLY so writes to flows.partitioning_to_flow are not blocked. This file must contain only this
-- statement: Flyway then runs it outside of a transaction, which CONCURRENTLY requires.
-- If the build fails, it leaves an INVALID index behind; drop it
-- (DROP INDEX CONCURRENTLY flows.partitioning_to_flow_idx1) and re-run the migration.
CREATE INDEX CONCURRENTLY partitioning_to_flow_idx1 ON flows.partitioning_to_flow (fk_partitioning);
