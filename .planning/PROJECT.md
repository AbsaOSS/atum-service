# Project Context

**Core Value:**
Atum must answer audit questions efficiently: in one filtered, paginated query, return the last *N* months of
measurements for a dataset *and its lineage*, with each measurement tagged with its dataset. Checkpoint filters
(name, multi-value properties such as `executionID IN (...)`, time window) combine server-side instead of being
applied one after another by the client.

**What This Is:**
Enhancements to `database`, `atum-server` and `atum-reader` that implement the consumption pattern in
`adrs/02-consumption-pattern-for-audits.md`: server-side time windows, a unified reader filter API, lineage-complete
audit scope, partial-key parent lookup, and recording explicit lineage edges. Milestone v1.1 delivers the
first two; the rest is deferred.

**Current milestone:** v1.1 Audit Consumption (ADR 002). See `.planning/ROADMAP.md` and `.planning/REQUIREMENTS.md`.

**Constraints:**
- Must stay backward compatible for API and reader consumers. New query params and DB function params are optional
  and trailing; deprecate before removing.
- A DB migration must not break a server that is still on the previous release (new function params get defaults).
- No re-architecture of checkpoint storage in this milestone. Table partitioning and normalized keys are deferred.

## Requirements

### Validated
- ✓ Read checkpoints by exact name (existing)
- ✓ Server-side pagination (existing)
- ✓ Server filters checkpoints by multi-value properties, e.g. `executionID IN (a,b,c)` — v1.0, CHK-01/02
- ✓ Pagination is accurate over the combined name + multi-property filter — v1.0, CHK-03

### Active
- [ ] Server-side `from`/`to` time window on flow and partitioning checkpoint queries, backed by an index (Phase 1)
- [ ] Reader: one `getCheckpointsPage` with a combined name + multi-value properties + time-window filter, plus a
  drain-all-pages helper (Phase 2; carries over v1.0 CHK-04/05)

### Out of Scope
- Major architectural changes to checkpoint storage.
- Lineage-complete audit scope, partial-key partitioning search, recording lineage edges (deferred 2026-09-25).
- Access control for audit consumers.
- Changes to the agent's checkpoint write path.

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Update both server and reader | Server only supported `Map[String, String]`, so backend IN-list support had to come before the reader could expose it. | ✓ Server done (v1.0); reader in v1.1 Phase 2 |
| Composite B-tree `(fk_partitioning, process_start_time)` before BRIN | Both endpoints scan checkpoints per partitioning. `process_start_time` is client-supplied, so the physical ordering BRIN relies on is not guaranteed. | ✓ Adopted; BRIN deferred |
| Main flow ≠ lineage chain | Verified in SQL: `main-flow(X) = {X} ∪ descendants(X)`. ADR §3 step 2 misses the upstream unless X is a root. | Documented as a known limitation; fix deferred |
| Time window on `process_start_time` | Business time is what audits ask about. | ✓ Decided 2026-09-25 |
| Amend unreleased `V0.8.0.1`/`V0.8.0.2` in place | One function version per release. | ✓ Decided 2026-09-25 |
| `CREATE INDEX CONCURRENTLY`, one index per migration | No write lock on `checkpoints` while the index builds. | ✓ Decided 2026-09-25 |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-09-25 — milestone v1.1 (ADR 002) started*
