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
-- Create lineage schema for partitioning parent-child relationships
--
-- Context:
--   Lineage functionality was initially developed in flows and runs schemas.
--   This migration consolidates lineage-specific objects into a dedicated
--   schema for better semantic organization and clearer boundaries.
--
-- Objects to be migrated here:
--   - flows.partitioning_edges table (direct parent-child edges)
--   - flows._link_to_parents function (made public as link_to_parents)
--   - flows.get_direct_parents, get_direct_children, get_partitioning_lineage_graph
--   - runs.get_partitioning_ancestors
--
-- Design:
--   The lineage schema holds the source of truth for partitioning relationships.
--   Edges are explicit and forward-only (v0.11.0+, no historical backfill).
--   The flows schema will eventually be retired, with lineage as the primary
--   relationship tracking mechanism.
-------------------------------------------------------------------------------

CREATE SCHEMA IF NOT EXISTS lineage;
ALTER SCHEMA lineage OWNER TO atum_owner;

GRANT USAGE ON SCHEMA lineage TO atum_user;
GRANT USAGE ON SCHEMA lineage TO atum_reader;
