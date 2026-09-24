# Project Context

**Core Value:**
The Atum system must support efficient, paginated querying of checkpoints by combining both checkpoint name and multi-value properties (e.g., `executionID IN (...)`), avoiding sequential single-property queries.

**What This Is:**
An enhancement to both `atum-reader` and `atum-server` to expand checkpoint filtering capabilities.

**Constraints:**
- Must maintain backward compatibility or provide a clear migration path for existing API consumers.
- Server-side changes required (updating `checkpointProperties` from `Map[String, String]` to support multi-value matching, e.g., `Map[String, Seq[String]]` or a dedicated field).

## Requirements

### Validated
- ✓ Read checkpoints by exact name (existing)
- ✓ Read checkpoints by single-value properties map (existing)
- ✓ Server-side pagination (existing)

### Active
- [ ] Reader exposes a single `getCheckpointsPage` method that accepts both `checkpointName` and `checkpointProperties`.
- [ ] Server API allows filtering checkpoints by a list/set of values for a property (e.g., `executionID IN (a,b,c)`).
- [ ] Reader exposes the multi-value property filtering capability in its API.
- [ ] Server API applies pagination (`limit`, `offset`) across the combined (name + multi-property) result set accurately.

### Out of Scope
- Major architectural changes to checkpoint storage.

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Update both server and reader | Server currently only supports `Map[String, String]`, so we must add backend support for IN-lists before the reader can expose it. | — Pending |

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
*Last updated: 2026-09-24 after initialization*
