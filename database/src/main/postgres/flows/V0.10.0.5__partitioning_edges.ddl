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
-- Migration: V0.10.0.5__partitioning_edges.ddl
-- Purpose: Create explicit parent-child edge table for direct lineage tracking
--
-- Context:
--   The existing flows.partitioning_to_flow table stores the materialized
--   transitive closure (A + all descendants of A) which enables fast
--   checkpoint queries and downstream navigation. However, it cannot
--   distinguish direct parents from transitive ancestors.
--
--   This new table stores explicit parent→child edges, enabling:
--   - Direct parent queries (immediate upstream dependencies)
--   - Accurate lineage graph visualization
--   - Enrichment of ancestor/descendant queries with relationship types
--
-- Storage:
--   O(n) - one row per parent-child relationship
--   Much more efficient than closure table for linear lineages
--
-- Query strategy:
--   - Direct queries (parents/children): Use edges table (~1ms)
--   - Transitive queries (ancestors/descendants): Use flows + edges enrichment (~5-10ms)
--
-- References:
--   - ADR 02: Audit Consumption (lineage semantics)
--   - ADR 03: Lineage Capability (closure invariant)
-------------------------------------------------------------------------------

CREATE TABLE flows.partitioning_edges
(
    fk_child_partitioning  BIGINT NOT NULL,
    fk_parent_partitioning BIGINT NOT NULL,
    created_by             TEXT NOT NULL,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT partitioning_edges_pk
        PRIMARY KEY (fk_child_partitioning, fk_parent_partitioning),

    CONSTRAINT partitioning_edges_child_fk
        FOREIGN KEY (fk_child_partitioning)
        REFERENCES runs.partitionings(id_partitioning)
        ON DELETE CASCADE,

    CONSTRAINT partitioning_edges_parent_fk
        FOREIGN KEY (fk_parent_partitioning)
        REFERENCES runs.partitionings(id_partitioning)
        ON DELETE CASCADE,

    -- Prevent self-loops (A→A)
    CONSTRAINT partitioning_edges_no_self_loop
        CHECK (fk_child_partitioning <> fk_parent_partitioning)
);

-- Indexes for efficient queries.
--
-- "Who are the parents of X?" (WHERE fk_child_partitioning = X) is the most common query
-- (auditors trace upstream). It is served by the PRIMARY KEY index, whose leading column is
-- fk_child_partitioning, so no dedicated index is needed for it - a separate index on
-- (fk_child_partitioning) or on (fk_child_partitioning, fk_parent_partitioning) would just
-- duplicate the PK index and add write/storage cost.
--
-- "Who are the children of X?" (WHERE fk_parent_partitioning = X, impact analysis) is NOT a
-- prefix of the PK, so it needs its own index. This index also backs the ON DELETE CASCADE
-- that fires on fk_parent_partitioning when a parent partitioning is removed.
CREATE INDEX partitioning_edges_parent_idx
    ON flows.partitioning_edges(fk_parent_partitioning);

ALTER TABLE flows.partitioning_edges OWNER TO atum_owner;

COMMENT ON TABLE flows.partitioning_edges IS
'Explicit parent→child edges for direct lineage relationships.
One row per direct dependency. Complements flows.partitioning_to_flow
which stores the transitive closure.';

COMMENT ON COLUMN flows.partitioning_edges.fk_child_partitioning IS
'The child partitioning (derived from parents)';

COMMENT ON COLUMN flows.partitioning_edges.fk_parent_partitioning IS
'The parent partitioning (source/input)';

COMMENT ON COLUMN flows.partitioning_edges.created_by IS
'User who established this parent-child relationship';

COMMENT ON COLUMN flows.partitioning_edges.created_at IS
'When this parent-child relationship was established';
