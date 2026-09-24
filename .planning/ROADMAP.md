# Execution Roadmap

## [x] Phase 1: Server Backend Support for Multi-Value Filters
**Goal:** Enhance the database queries and API models in `atum-server` to support `IN`-list property filters.
**Requirements:** CHK-01, CHK-02
**Success Criteria:**
1. Database queries correctly apply `IN` clause for multi-value property maps.
2. `GetFlowCheckpoints` and `GetPartitioningCheckpoints` endpoints accept multi-value structure (e.g. `checkpointProperties` mapped to lists).

## [ ] Phase 2: Server Pagination Accuracy
**Goal:** Ensure pagination works seamlessly across the new combined filter.
**Requirements:** CHK-03
**Success Criteria:**
1. Limit and offset are honored accurately when both `checkpointName` and multi-value `checkpointProperties` are used simultaneously.
2. Integration tests verify correct page sizes and bounds.

## [ ] Phase 3: Reader API Enhancements
**Goal:** Update `atum-reader` to expose the new server capabilities.
**Requirements:** CHK-04, CHK-05
**Success Criteria:**
1. Reader exposes `getCheckpointsPage` combining name and properties.
2. Reader property map signature supports sets/lists (e.g., `Map[String, Set[String]]`).
3. Reader HTTP client formats the query correctly for the server.
