# Phase 1 Context

**Goal:** Enhance the database queries and API models in `atum-server` to support `IN`-list property filters.

## Canonical Refs
- `.planning/ROADMAP.md`
- `.planning/REQUIREMENTS.md`

## Decisions Captured

### API Contract for Multi-Value Filters
- We will change the JSON structure of the base64url-encoded `checkpoint-properties` query parameter to allow arrays for values (i.e. `Map[String, Seq[String]]`).

### Code Context
- The `checkpointProperties` type in Tapir endpoints (`server/src/main/scala/za/co/absa/atum/server/api/v2/endpoints/...`) and controller/service layers must be updated to `Option[Map[String, Seq[String]]]`.
- Database access logic (`Doobie` fragments in `server/src/main/scala/za/co/absa/atum/server/api/database/...`) must translate these map entries into SQL `IN` clauses when multiple values are provided.

## Deferred Ideas
- None
