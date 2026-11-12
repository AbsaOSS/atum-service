# ADR 02: Explicit Lineage Modeling, Dedicated Lineage Schema, and Direct Cutover

## Status
Accepted

## Context
Historically in Atum Service:
1. **Implicit transitive closure only**: Partitioning lineage relationships were modeled primarily through a transitive closure table (`flows.partitioning_to_flow`). While this enabled efficient "is A an ancestor of B?" queries, it could not distinguish direct (1-hop) parent-child relationships from multi-hop transitive ancestors. Consequently, reconstructing an execution DAG or building visualization graphs required expensive heuristics or was unsupported.
2. **Scattered lineage operations**: Lineage functions were split between the `flows` schema (e.g. `_link_to_parents`, `get_direct_parents`, `get_direct_children`, `get_partitioning_lineage_graph`) and the `runs` schema (e.g. `get_partitioning_ancestors`), obscuring bounded contexts and domain responsibilities.
3. **Deployment model**: Atum Service runs against a dedicated PostgreSQL database instance where the server application and database migrations are versioned and deployed concurrently as a unit. Maintaining backward-compatibility SQL wrappers across multiple schema versions introduces technical debt, dead code paths, and testing complexity without providing operational benefit.

## Decision

1. **Explicit Edge Table**:
   - Model direct 1-hop parent-child dependencies explicitly in `lineage.partitioning_edges (parent_partitioning_id, child_partitioning_id, created_at, created_by)`.
   - Index the edge table on both `(parent_partitioning_id, child_partitioning_id)` (primary key) and `(child_partitioning_id, parent_partitioning_id)` for bidirectional $O(1)$ lookups.

2. **Dedicated `lineage` Database Schema**:
   - Consolidate all lineage-related database tables and functions into a new `lineage` schema:
     - Table: `lineage.partitioning_edges`
     - Public Functions:
       - `lineage.link_to_parents(BIGINT, BIGINT[], TEXT)`: Validates acyclicity and leaf constraints, inserts direct edges, and dual-writes to the `flows.partitioning_to_flow` transitive closure.
       - `lineage.get_direct_parents(BIGINT)`: Returns immediate parent partitionings.
       - `lineage.get_direct_children(BIGINT)`: Returns immediate child partitionings.
       - `lineage.get_partitioning_ancestors(BIGINT, INT, BIGINT)`: Returns paginated transitive ancestors.
       - `lineage.get_partitioning_lineage_graph(BIGINT, TEXT, INTEGER)`: Returns a bounded sub-graph (nodes + edges JSON) for DAG visualization without $N+1$ query overhead.

3. **Direct Cutover (No Backward-Compatibility Wrappers)**:
   - Drop deprecated wrappers in `flows` and `runs` during migration `V0.10.0.13` through `V0.10.0.17`.
   - Server application code and database schema are deployed together. Direct cutover eliminates legacy wrapper maintenance and ambiguity.

4. **Transitive Closure Coexistence & Dual-Write**:
   - Retain `flows.partitioning_to_flow` updated atomically within `lineage.link_to_parents` for backward-compatible flow queries.
   - Paves the way for future milestone retirement of the `flows` closure table once all flows use recursive CTEs over `lineage.partitioning_edges`.

## Consequences

### Positive
- **Deterministic 1-Hop Lineage**: Accurate graph visualization and DAG reconstruction without loss of granularity.
- **Clear Domain Boundaries**: Partitioning CRUD and checkpoint operations reside in `runs`, transitive flow queries in `flows`, and all graph/edge operations in `lineage`.
- **Zero Legacy Overhead**: Clean database namespace without delegating stub functions or duplicated integration tests.
- **High Performance Visualization**: The `get_partitioning_lineage_graph` function computes bidirectional sub-graphs in a single PostgreSQL query with bounded depth (default 5, max 20) and cycle prevention.

### Negative / Trade-offs
- **Dual-Write Cost**: During parent linking, writes touch both `lineage.partitioning_edges` and `flows.partitioning_to_flow` within the same transaction under the global advisory lock.
- **Strict Coupling on Upgrade**: Database migrations and server binaries must be applied in lockstep (acceptable given the single-database deployment architecture).
