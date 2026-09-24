# Phase 2: Server Pagination Accuracy - Plan

## Scope
**Phase:** 02
**Mode:** revision
**Requirements:** CHK-03

## Execution Strategy
The goal of this phase is to ensure that pagination limits and offsets accurately apply on the combined filtered result sets in the server backend, specifically when both `checkpointName` and multi-value `checkpointProperties` are provided simultaneously. We will achieve this by extending our database integration test suites and addressing any discrepancies found within the latest database function migrations.

1. **Test Expansion**: Expand integration tests in `GetFlowCheckpointsIntegrationTests` and `GetPartitioningCheckpointsIntegrationTests` to execute queries with both a specific `checkpointName` and a map of `checkpointProperties` (using multiple values) while strictly validating that the correct number of results are returned up to `limit` and offset matches expectations.
2. **Implementation Verification / Fix**: Run the new test scenarios against the database functions. If the returned pages contain more elements than the limit or start at incorrect offsets when the combined filters are active, create new PostgreSQL migrations (e.g., `V0.7.0.3__get_flow_checkpoints_pagination_fix.sql` and `V0.7.0.4__get_partitioning_checkpoints_pagination_fix.sql`) to accurately apply pagination (`LIMIT`/`OFFSET`) to the `limited_checkpoints` CTE (which operates on `runs.checkpoints`), and not at the outermost query level, rather than modifying the applied migrations from Phase 1.
3. **Regression Check**: Ensure existing tests pass without issue.

## Tracked Files
### `files_modified`
- `server/src/test/scala/za/co/absa/atum/server/api/database/flows/functions/GetFlowCheckpointsIntegrationTests.scala`
- `server/src/test/scala/za/co/absa/atum/server/api/database/runs/functions/GetPartitioningCheckpointsIntegrationTests.scala`
- `database/src/main/postgres/flows/V0.7.0.3__get_flow_checkpoints_pagination_fix.sql`
- `database/src/main/postgres/runs/V0.7.0.4__get_partitioning_checkpoints_pagination_fix.sql`

### `must_haves.artifacts`
- None
