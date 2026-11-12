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

-------------------------------------------------------------------------------
-- Move partitioning_edges from flows to lineage schema
--
-- Strategy: ALTER TABLE SET SCHEMA is atomic and doesn't require data copying
-- All constraints, indexes, and permissions move with the table automatically
--
-- Objects that move:
--   - Table: flows.partitioning_edges -> lineage.partitioning_edges
--   - Primary key: partitioning_edges_pk
--   - Index: partitioning_edges_parent_idx
--   - Check constraint: partitioning_edges_no_self_loop
--
-- Impact: Zero downtime (atomic operation, milliseconds)
-------------------------------------------------------------------------------

-- Move the table (atomic operation)
ALTER TABLE flows.partitioning_edges SET SCHEMA lineage;

-- Verify ownership and permissions (should carry over, but explicit for clarity)
ALTER TABLE lineage.partitioning_edges OWNER TO atum_owner;
GRANT SELECT ON lineage.partitioning_edges TO atum_reader;

-- Update comment to reflect new location
COMMENT ON TABLE lineage.partitioning_edges IS
'Explicit parent->child edges for direct lineage relationships.
One row per direct dependency. Complements flows.partitioning_to_flow
which stores the transitive closure.

Migrated from flows.partitioning_edges in V0.10.0.12';
