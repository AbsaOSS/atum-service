# Phase 2: Server Pagination Accuracy - Context

**Gathered:** 2026-09-24
**Status:** Ready for planning

<domain>
## Phase Boundary

An enhancement to both `atum-reader` and `atum-server` to expand checkpoint filtering capabilities by combining both checkpoint name and multi-value properties (e.g., `executionID IN (...)`), avoiding sequential single-property queries. It ensures pagination accuracy on the server side and exposes backward-compatible methods on the reader.

</domain>

<decisions>
## Implementation Decisions

### Reader API backward compatibility
- **D-01:** Deprecate old single-filter methods (e.g. `getCheckpoints(name)`) and encourage moving to the new combined `getCheckpointsPage` method. — **Reversibility:** costly — breaking change requires migration for API consumers over time.

### Pagination response wrapper
- **D-02:** Return a `PaginatedResult[Checkpoint]` wrapper (with total/next page metadata if server supports it) from the Reader API.

### the agent's Discretion
- Internal handling of properties (e.g. converting `Set[String]` to `Seq[String]` before sending the request to Tapir client, or simply using `Seq` throughout if that matches the server contract).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project Requirements & Roadmap
- `.planning/ROADMAP.md` — Project roadmap and phase definitions.
- `.planning/REQUIREMENTS.md` — Milestone requirements, including CHK-01 through CHK-05 for pagination and multi-value filters.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `server/src/main/scala/za/co/absa/atum/server/api/v2/endpoints/...`: Existing Tapir endpoints for Checkpoints that will need updating to support multi-value filters and pagination.
- `server/src/main/scala/za/co/absa/atum/server/api/database/...`: Existing Doobie fragments for checkpoint queries that will need `IN` clause translation.
- `reader`: Client library where the new `getCheckpointsPage` and `PaginatedResult[Checkpoint]` class will live.

### Established Patterns
- Tapir is used for declaring API endpoints declaratively.
- ZIO is used for dependency injection and effect management in the server.
- Circe is used for JSON serialization.
- Cross-building with `projectMatrix` in SBT.

### Integration Points
- `atum-reader` API boundary where client applications request paginated data.
- Server-side REST API definition (Tapir endpoints).
- Server-side database access layer (Doobie).

</code_context>

<specifics>
## Specific Ideas

- The new combined method in `atum-reader` should be named `getCheckpointsPage` as per the requirements.
- Ensure the `PaginatedResult[Checkpoint]` wrapper matches the server-side pagination metadata (limit, offset, total count / hasNextPage).

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 2-Server Pagination Accuracy*
*Context gathered: 2026-09-24*
