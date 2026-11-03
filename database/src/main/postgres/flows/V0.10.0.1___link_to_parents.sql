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

CREATE OR REPLACE FUNCTION flows._link_to_parents(
    IN  i_child_id          BIGINT,
    IN  i_parent_ids        BIGINT[],
    IN  i_by_user           TEXT,
    OUT status              INTEGER,
    OUT status_text         TEXT
) RETURNS record AS
-------------------------------------------------------------------------------
--
-- Function: flows._link_to_parents(3)
--      The single place that links an (existing) child partitioning under one or more parent partitionings:
--      it rejects self-links and cycles, serializes writers with one global advisory lock and propagates the child
--      (and all of its descendants) into every flow that contains a parent.
--      Used by runs.create_partitioning_with_parents and runs.update_partitioning_parent.
--
-- Closure-table invariant: members(F(A)) = {A} U descendants(A), where F(A) is the flow whose primary partitioning is A.
--      The ancestors of X are the primary partitionings of the flows containing X (excluding X).
--
-- Parameters:
--      i_child_id              - id of the child partitioning (must already exist)
--      i_parent_ids            - ids of the parent partitionings (must already exist); NULL elements and duplicates
--                                are ignored, an empty or NULL array means "no parents"
--      i_by_user               - user to be stored as the author of the newly created flow memberships
--
-- Returns:
--      status                  - Status code
--      status_text             - Status text (for status 32 it names the offending partitioning ids, comma separated)
--
-- Status codes:
--      11 - Links written
--      14 - Nothing to do (every parent is already a direct or transitive ancestor, or there are no parents)
--      32 - A parent is the child itself or one of its descendants (the link would create a cycle)
--
-- Notes:
--      - Self-link is rejected BEFORE the fast path (the child is a member of its own flow and would otherwise look
--        "already linked").
--      - Status 32 is always returned before the first write; fa-db commits first and inspects the status afterwards,
--        so every non-OK status MUST precede any write.
--      - Fast path: when every parent already has the child in its own flow, nothing is written and NO lock is taken.
--      - Lock: one global exclusive transaction-scoped advisory lock, taken only when the fast path does not apply
--        (this includes a brand-new child with parents). The statements after the lock re-check under a fresh
--        snapshot - sound ONLY under READ COMMITTED (Hikari default); do NOT set REPEATABLE READ/SERIALIZABLE
--        anywhere. The lock must stay a standalone PERFORM (never inside a SELECT ... WHERE).
--      - No lock_timeout (P8-Q4, locked decision): a waiting writer holds a pool connection (Hikari max 10).
--        Accepted residual risk; slow-path calls are short and re-links of existing ancestors take the lock-free
--        fast path. To be documented in README/ADR in Phase 11.
--      - The v1 path runs.create_partitioning does not take this lock; it links only a brand-new child (no
--        descendants), so it cannot lose an update.
--      - Callers must not hold a FOR UPDATE row lock when calling this function (lock-order inversion vs. the
--        advisory lock).
--
-------------------------------------------------------------------------------
$$
DECLARE
    _parent_ids     BIGINT[];
    _offending      BIGINT[];
    _rows           INTEGER;
BEGIN
    _parent_ids := ARRAY(
        SELECT DISTINCT PID.id FROM unnest(i_parent_ids) AS PID(id) WHERE PID.id IS NOT NULL ORDER BY PID.id
    );

    -- self-link: the child is a member of its own flow and would pass the fast path; reject it first
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

    -- every flow containing a parent receives the child and all members of the child's flow (its descendants)
    WITH MEMBERS AS (
        SELECT i_child_id AS id_member
        UNION
        SELECT CPTF.fk_partitioning
        FROM flows.flows CF
        INNER JOIN flows.partitioning_to_flow CPTF ON CPTF.fk_flow = CF.id_flow
        WHERE CF.fk_primary_partitioning = i_child_id
    )
    INSERT INTO flows.partitioning_to_flow (fk_flow, fk_partitioning, created_by)
    SELECT DISTINCT PPTF.fk_flow, M.id_member, i_by_user
    FROM unnest(_parent_ids) AS PID(id)
    INNER JOIN flows.partitioning_to_flow PPTF ON PPTF.fk_partitioning = PID.id
    CROSS JOIN MEMBERS M
    ON CONFLICT DO NOTHING;
    GET DIAGNOSTICS _rows = ROW_COUNT;

    IF _rows > 0 THEN
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
