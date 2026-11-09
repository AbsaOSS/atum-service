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
-- Migration: V0.10.0.3__link_to_parents_with_edges.sql
-- Purpose: Modify _link_to_parents to dual-write to both edges and flows tables
--
-- Changes:
--   - Adds INSERT to flows.partitioning_edges (explicit parent-child relationships)
--   - Keeps existing INSERT to flows.partitioning_to_flow (transitive closure)
--   - Edge insert happens FIRST (fail fast on constraint violations)
--   - Both inserts are idempotent (ON CONFLICT DO NOTHING)
--   - Same transaction ensures atomicity
--
-- Behavior:
--   - Status 11 returned if ANY rows written (edges or flows)
--   - Status 14 returned if NOTHING written (fully idempotent retry)
--   - Edge constraints (no self-loops, FKs) checked before flow writes
--
-- Inherited invariants (unchanged from V0.10.0.1 - do NOT regress these):
--   - Status codes: 11 linked, 14 nothing-to-do, 32 cycle/self-link, 33 child-has-descendants.
--   - Closure invariant: members(F(A)) = {A} U descendants(A). Only a leaf is ever linked under a
--     new parent; a child with descendants is refused (33) rather than dragging them along.
--   - Fast path (no lock) when every parent already has the child in its own primary flow; the same
--     check is repeated under the lock so idempotent retries that waited on a concurrent writer still
--     return 14. Self-link is rejected (32) before the fast path.
--   - Concurrency: one global transaction-scoped advisory lock, taken only on the slow path. Sound
--     ONLY under READ COMMITTED (Hikari default) - do NOT set REPEATABLE READ / SERIALIZABLE, and keep
--     the lock a standalone PERFORM. No lock_timeout is set (deliberate; callers must not hold a
--     FOR UPDATE row lock when calling this function - lock-order inversion vs. the advisory lock).
--
-- CHANGED failure mode (vs V0.10.0.1) - a non-existent parent id now raises an FK violation on the
--   edge insert and aborts the transaction, where the old flows-only path silently ignored it and
--   returned 14. Callers that previously tolerated stale/unknown parent ids must validate parents
--   exist (or map the FK violation to a clean 4xx) rather than surfacing a 500.
--
-- Edge coverage is FORWARD ONLY, and this is intentional (no backfill planned). Edges are written
--   only by this v2 path; the deprecated v1 runs.create_partitioning links via _add_to_parent_flows
--   and writes none, and pre-v0.11.0 relationships have none. The fast path short-circuits on closure
--   membership before the edge insert, so re-linking does NOT back-fill (status 14), and direct
--   parentage cannot be reconstructed from the closure anyway (it does not distinguish direct from
--   transitive). GET /parents is therefore empty for v1-created and pre-upgrade lineage by design:
--   lineage is opt-in and only accrues once data producers are configured for it on the Atum agent
--   (partitioning 'map' construction/reconstruction).
-------------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION flows._link_to_parents(
    IN  i_child_id          BIGINT,
    IN  i_parent_ids        BIGINT[],
    IN  i_by_user           TEXT,
    OUT status              INTEGER,
    OUT status_text         TEXT
) RETURNS record AS
$$
DECLARE
    _parent_ids     BIGINT[];
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

    -- self-link: the child is a member of its own flow, reject it
    IF i_child_id = ANY(_parent_ids) THEN
        status := 32;
        status_text := 'Parent partitioning would create a cycle: ' || i_child_id::TEXT;
        RETURN;
    END IF;

    -- fast path (no lock): every parent already has the child in its own (primary) flow, i.e. is an ancestor
    IF NOT EXISTS (
        SELECT 1
        FROM unnest(_parent_ids) AS PID(id)
        WHERE NOT EXISTS (
            SELECT 1
            FROM flows.flows F
            INNER JOIN flows.partitioning_to_flow PTF ON PTF.fk_flow = F.id_flow
            WHERE F.fk_primary_partitioning = PID.id AND PTF.fk_partitioning = i_child_id
        )
    ) THEN
        status := 14;
        status_text := 'Nothing has changed';
        RETURN;
    END IF;

    -- slow path: one global exclusive transaction-scoped lock. READ COMMITTED only: every statement below gets a
    -- fresh snapshot, so what a concurrent writer committed before releasing the lock is visible here.
    PERFORM pg_advisory_xact_lock(hashtextextended('atum.flows.link_to_parents', 0));

    -- no-op re-check under the lock: a concurrent writer may have linked the child to every parent while this call
    -- waited (e.g. an idempotent retry), it must stay a no-op even if the child meanwhile got descendants
    IF NOT EXISTS (
        SELECT 1
        FROM unnest(_parent_ids) AS PID(id)
        WHERE NOT EXISTS (
            SELECT 1
            FROM flows.flows F
            INNER JOIN flows.partitioning_to_flow PTF ON PTF.fk_flow = F.id_flow
            WHERE F.fk_primary_partitioning = PID.id AND PTF.fk_partitioning = i_child_id
        )
    ) THEN
        status := 14;
        status_text := 'Nothing has changed';
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
    -- would drag them into the flows of the new parents, so such a link is refused
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

    -- 1. INSERT EDGES FIRST (NEW): Explicit parent→child relationships
    --    This fails fast on constraint violations (no self-loops, valid FKs)
    INSERT INTO flows.partitioning_edges (fk_child_partitioning, fk_parent_partitioning, created_by)
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

ALTER FUNCTION flows._link_to_parents(BIGINT, BIGINT[], TEXT) OWNER TO atum_owner;
REVOKE EXECUTE ON FUNCTION flows._link_to_parents(BIGINT, BIGINT[], TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION flows._link_to_parents(BIGINT, BIGINT[], TEXT) TO atum_owner;
