# Codebase Structure

**Analysis Date:** 2026-09-24

## Directory Layout

```
/
├── adrs/          # Architecture Decision Records
├── agent/         # Spark plugin for measuring and sending data
├── api-tests/     # Integration and E2E tests for the API
├── database/      # Database migrations and Doobie fragments
├── model/         # Shared domain models
├── project/       # SBT build configuration and dependencies
├── reader/        # Client library for reading data from server
└── server/        # The ZIO HTTP4s Tapir REST API server
```

## Directory Purposes

**`agent/`:**
- Purpose: Spark library to capture metrics
- Contains: Scala code

**`server/`:**
- Purpose: Backend service
- Contains: ZIO services, Tapir endpoints, Doobie queries

**`model/`:**
- Purpose: Shared DTOs
- Contains: Case classes and Circe codecs

**`database/`:**
- Purpose: DB definition
- Contains: Flyway SQL migrations

## Entry Points

**Configuration:**
- `build.sbt`: Multi-module SBT definition
- `project/Dependencies.scala`: All library versions

## Where to Add New Code

**New API Endpoint:**
- Define in `server/src/.../endpoints` using Tapir
- Implement logic in `server/src/.../services`
- Add shared DTOs to `model/`

**New Spark metric:**
- Add to `agent/`

<!-- refreshed: 2026-09-24 -->
