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
-- Design:
--   The lineage schema holds the source of truth for partitioning relationships.
--   Edges are explicit and forward-only. The flows schema will eventually be retired,
--   with lineage as the primary relationship tracking mechanism.
-------------------------------------------------------------------------------

CREATE SCHEMA IF NOT EXISTS lineage;
ALTER SCHEMA lineage OWNER TO atum_owner;

GRANT USAGE ON SCHEMA lineage TO atum_user;
GRANT USAGE ON SCHEMA lineage TO atum_reader;
