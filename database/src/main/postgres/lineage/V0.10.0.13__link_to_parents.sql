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

DROP FUNCTION IF EXISTS flows._link_to_parents(BIGINT, BIGINT[], TEXT);

-------------------------------------------------------------------------------
-- Link a child partitioning under one or more parent partitionings, maintaining both the transitive closure
--   (table flows.partitioning_to_flow) and the explicit direct-parentage edges (table lineage.partitioning_edges)
--   used to answer "who are the direct parents of X" without walking the whole closure.
--
-- Changes from flows._link_to_parents:
--   1. Schema: flows._link_to_parents -> lineage.link_to_parents (now public)
--   2. Table refs: flows.partitioning_edges -> lineage.partitioning_edges (4 occurrences)
--   3. Permissions: GRANT EXECUTE to atum_user (was atum_owner only)
--   4. Advisory lock name kept unchanged for safety: 'atum.flows.link_to_parents'
--
-- Closure invariant: members(F(A)) = {A} U descendants(A), where F(A) is the flow whose primary partitioning is A.
--   Only a leaf (no existing descendants) may be linked under a genuinely new parent;
--   a child that already has descendants is refused (33) rather than dragging them along.
--
-- Ancestor vs edge: whether a parent is "already an ancestor" (closure membership) and whether a
--   direct edge for it exists yet are two different questions. A parent can already be an ancestor with no edge.
--   Re-linking such a parent only needs to backfill the missing edge row; it never needs to touch the closure,
--   so it is always safe to do lock-free and without the leaf/cycle checks:
--     - an already-established ancestor cannot be a descendant of the child (that would already be a
--       cycle in the closure, which cannot exist), so adding its edge can never introduce one;
--     - by the closure invariant, if the child is already in F(parent) then every existing descendant
--       of the child is already in F(parent) too, so no flow membership ever needs to be added.
--   The leaf/descendants check (status 33) only applies when at least one requested parent is NOT yet
--   an ancestor (a genuinely new link, which may need real closure propagation).
--
-- Status codes:
--      11 - Partitioning linked to parents (edge and/or flow membership written, including an edge-only backfill
---                                           for an already-known ancestor)
--      14 - Nothing has changed (fully idempotent retry)
--      32 - Parent partitioning would create a cycle
--      33 - Partitioning already has descendants and cannot be linked under new parents
--      41 - Child partitioning not found
--      42 - Parent partitioning not found
--
-- Fast path (no lock): every requested parent is already an ancestor via the closure; only missing
--   lineage.partitioning_edges rows are backfilled (idempotent, ON CONFLICT DO NOTHING).
--
-- Slow path (1 global transaction-scoped advisory lock): taken only when at least 1 requested parent is genuinely new;
--   re-checks ancestry under the lock (an idempotent retry may have been satisfied by a concurrent writer
--   while waiting) and then runs the cycle check, the leaf/descendants check, and finally the dual write
--   to both lineage.partitioning_edges and flows.partitioning_to_flow.
--   Important: designed under READ COMMITTED (Hikari default) - do NOT set REPEATABLE READ / SERIALIZABLE,
--   and keep the lock a standalone PERFORM. No lock_timeout is set (deliberate; callers must not hold a
--   FOR UPDATE row lock when calling this function - lock-order inversion vs. the advisory lock).
-------------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION lineage.link_to_parents(
    IN  i_child_id          BIGINT,
    IN  i_parent_ids        BIGINT[],
    IN  i_by_user           TEXT,
    OUT status              INTEGER,
    OUT status_text         TEXT
) RETURNS record AS
$$
DECLARE
    _parent_ids     BIGINT[];
    _new_parents    BIGINT[];
    _offending      BIGINT[];
    _rows_edges     INTEGER;
    _rows_flows     INTEGER;
BEGIN
    -- normalize parents: distinct, non-NULL, deterministic order
    _parent_ids := ARRAY(
        SELECT DISTINCT PID.id
        FROM unnest(i_parent_ids) AS PID(id)
        WHERE PID.id IS NOT NULL
        ORDER BY PID.id
    );

    -- check child partitioning exists
    PERFORM 1 FROM runs.partitionings WHERE id_partitioning = i_child_id;
    IF NOT FOUND THEN
        status := 41;
        status_text := 'Child partitioning not found';
        RETURN;
    END IF;

    -- check existence of all parents
    PERFORM 1
    FROM unnest(_parent_ids) AS PID(id)
    WHERE NOT EXISTS (
        SELECT 1 FROM runs.partitionings P WHERE P.id_partitioning = PID.id
    );
    IF FOUND THEN
        status := 42;
        status_text := 'Parent partitioning not found';
        RETURN;
    END IF;

    -- self-link: the child is a member of its own flow, reject it
    IF i_child_id = ANY(_parent_ids) THEN
        status := 32;
        status_text := 'Parent partitioning would create a cycle: ' || i_child_id::TEXT;
        RETURN;
    END IF;

    -- parents that are NOT yet an ancestor (direct or transitive) of the child, i.e. genuinely new links
    _new_parents := ARRAY(
        SELECT PID.id
        FROM unnest(_parent_ids) AS PID(id)
        WHERE NOT EXISTS (
            SELECT 1
            FROM flows.flows F
            INNER JOIN flows.partitioning_to_flow PTF ON PTF.fk_flow = F.id_flow
            WHERE F.fk_primary_partitioning = PID.id AND PTF.fk_partitioning = i_child_id
        )
    );

    -- fast path (no lock): every parent is already an ancestor; only a missing direct edge may need to
    -- be backfilled (see migration header for why this can never touch the closure, no lock required)
    IF cardinality(_new_parents) = 0 THEN
        INSERT INTO lineage.partitioning_edges (fk_child_partitioning, fk_parent_partitioning, created_by)
        SELECT i_child_id, PID.id, i_by_user
        FROM unnest(_parent_ids) AS PID(id)
        ON CONFLICT (fk_child_partitioning, fk_parent_partitioning) DO NOTHING;
        GET DIAGNOSTICS _rows_edges = ROW_COUNT;

        IF _rows_edges > 0 THEN
            status := 11;
            status_text := 'Partitioning linked to parents';
        ELSE
            status := 14;
            status_text := 'Nothing has changed';
        END IF;
        RETURN;
    END IF;

    -- slow path: one global exclusive transaction-scoped lock. READ COMMITTED only: every statement below gets a
    -- fresh snapshot, so what a concurrent writer committed before releasing the lock is visible here.
    PERFORM pg_advisory_xact_lock(hashtextextended('atum.flows.link_to_parents', 0));

    -- no-op re-check under the lock (ancestor-only, as above): a concurrent writer may have linked the
    -- child to every parent while this call waited (e.g. an idempotent retry); it must stay on the
    -- lock-free-equivalent edge-backfill-only path even if the child meanwhile got descendants
    _new_parents := ARRAY(
        SELECT PID.id
        FROM unnest(_parent_ids) AS PID(id)
        WHERE NOT EXISTS (
            SELECT 1
            FROM flows.flows F
            INNER JOIN flows.partitioning_to_flow PTF ON PTF.fk_flow = F.id_flow
            WHERE F.fk_primary_partitioning = PID.id AND PTF.fk_partitioning = i_child_id
        )
    );
    IF cardinality(_new_parents) = 0 THEN
        INSERT INTO lineage.partitioning_edges (fk_child_partitioning, fk_parent_partitioning, created_by)
        SELECT i_child_id, PID.id, i_by_user
        FROM unnest(_parent_ids) AS PID(id)
        ON CONFLICT (fk_child_partitioning, fk_parent_partitioning) DO NOTHING;
        GET DIAGNOSTICS _rows_edges = ROW_COUNT;

        IF _rows_edges > 0 THEN
            status := 11;
            status_text := 'Partitioning linked to parents';
        ELSE
            status := 14;
            status_text := 'Nothing has changed';
        END IF;
        RETURN;
    END IF;

    -- cycle check (also the post-lock re-check): is a parent a member of the child's flow?
    _offending := ARRAY(
        SELECT DISTINCT PID.id
        FROM unnest(_parent_ids) AS PID(id)
        INNER JOIN flows.partitioning_to_flow PTF ON PTF.fk_partitioning = PID.id
        INNER JOIN flows.flows F ON F.id_flow = PTF.fk_flow
        WHERE F.fk_primary_partitioning = i_child_id
        ORDER BY PID.id
    );
    IF cardinality(_offending) > 0 THEN
        status := 32;
        status_text := 'Parent partitioning would create a cycle: ' || array_to_string(_offending, ',');
        RETURN;
    END IF;

    -- leaf check (also under the lock): a child with descendants (any member of its own flow other than itself)
    -- would drag them into the flows of a genuinely NEW parent (at least one in _new_parents), so such a link is
    -- refused; already-ancestor parents never reach this far (handled above, lock-free)
    PERFORM 1
    FROM flows.flows CF
    INNER JOIN flows.partitioning_to_flow CPTF ON CPTF.fk_flow = CF.id_flow
    WHERE CF.fk_primary_partitioning = i_child_id AND CPTF.fk_partitioning <> i_child_id;
    IF FOUND THEN
        status := 33;
        status_text := 'Partitioning already has descendants and cannot be linked under new parents: ' || i_child_id::TEXT;
        RETURN;
    END IF;

    -- =========================================================================
    -- DUAL WRITE: Insert into both edges and flows tables
    -- =========================================================================

    -- 1. INSERT EDGES FIRST (NEW): Explicit parent->child relationships
    INSERT INTO lineage.partitioning_edges (fk_child_partitioning, fk_parent_partitioning, created_by)
    SELECT i_child_id, PID.id, i_by_user
    FROM unnest(_parent_ids) AS PID(id)
    ON CONFLICT (fk_child_partitioning, fk_parent_partitioning) DO NOTHING;
    GET DIAGNOSTICS _rows_edges = ROW_COUNT;

    -- 2. INSERT FLOWS (EXISTING): Transitive closure (child joins all ancestor flows)
    INSERT INTO flows.partitioning_to_flow (fk_flow, fk_partitioning, created_by)
    SELECT DISTINCT PPTF.fk_flow, i_child_id, i_by_user
    FROM unnest(_parent_ids) AS PID(id)
    INNER JOIN flows.partitioning_to_flow PPTF ON PPTF.fk_partitioning = PID.id
    ON CONFLICT DO NOTHING;
    GET DIAGNOSTICS _rows_flows = ROW_COUNT;

    -- Return status based on whether ANY writes occurred
    IF _rows_edges > 0 OR _rows_flows > 0 THEN
        status := 11;
        status_text := 'Partitioning linked to parents';
    ELSE
        status := 14;
        status_text := 'Nothing has changed';
    END IF;
    RETURN;
END;
$$
LANGUAGE plpgsql VOLATILE SECURITY DEFINER
SET search_path = pg_catalog, public, pg_temp;

ALTER FUNCTION lineage.link_to_parents(BIGINT, BIGINT[], TEXT) OWNER TO atum_owner;
REVOKE EXECUTE ON FUNCTION lineage.link_to_parents(BIGINT, BIGINT[], TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION lineage.link_to_parents(BIGINT, BIGINT[], TEXT) TO atum_user;

COMMENT ON FUNCTION lineage.link_to_parents(BIGINT, BIGINT[], TEXT) IS
'Link a child partitioning to one or more parent partitionings.
Maintains both the explicit edge table (lineage.partitioning_edges) and
the transitive closure (flows.partitioning_to_flow).

Migrated from flows._link_to_parents and made public in V0.10.0.13';


