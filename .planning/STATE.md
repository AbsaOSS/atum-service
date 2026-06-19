# Project State

**Project:** atum-service — DB Connection Bottleneck Improvements
**Issue:** [#205](https://github.com/absa-group/atum-service-deployment/issues/205)
**Initialized:** 2026-05-21
**Branch:** feature/205-db-connection-bottlenecks-improvements

## Project Reference

See: `.planning/PROJECT.md` (updated 2026-05-22)

**Core value:** Consumers never see timeout errors caused by DB connection pool exhaustion under burst load
**Current focus:** Milestone archived ✅ — no active milestone

## Current Status

**Active Phase:** None — milestone complete
**Phase Goal:** —
**Current Plan:** —

## Progress

- ✅ Plan 01-01: `connectionTimeout: Long` added to `PostgresConfig`; wired into `TransactorProvider` HikariCP block
- ✅ Plan 01-02: `connectionTimeout=60000` HOCON default in both `reference.conf` files; `PostgresConfigUnitTests` (2 tests pass)
- ✅ Plan 02-01: `SemaphoreProvider.scala` created (`TaskLayer[Semaphore]`, permits = (maxPoolSize-1).max(1).toLong)
- ✅ Plan 02-02: All 17 `repositoryCall(...)` sites wrapped with `semaphore.withPermit`; `SemaphoreProvider.layer` wired in `Main.scala`
- ✅ Plan 02-03: 5 service test `.provide()` blocks fixed; `SemaphoreQueuingUnitTests` (2 tests); 214 unit tests pass
- ✅ Plan 03-01: `RetryConfig` case class with `Default` (3, 500 ms, 10 s) and `fromConfig` with `hasPath` guards; retry keys documented in both `reference.conf` files
- ✅ Plan 03-02: `withRetry` loop in `HttpDispatcher` (5xx + IOException retry, 4xx fail-fast, exponential backoff + jitter); all 7 `backend.send` sites wrapped; 12 unit tests pass

## Decisions

- Use `Long` not `Int` for `connectionTimeout` — matches HikariCP `setConnectionTimeout(long)` JVM primitive; avoids silent truncation above `Int.MaxValue` (~2.1B ms)
- Compile gate deferred to Plan 01-02: compiling with new field but no HOCON key throws `ConfigException` at ZIO config derivation
- Used `config.hasPath` guards per-key in `RetryConfig.fromConfig` — prevents `ConfigException` when retry block absent; enables partial overrides
- Use `Answer<T>` (throws Throwable) instead of `thenThrow` for IOException mocking — Mockito rejects checked exceptions on non-declaring interface methods
- `withRetry` rewritten with `Option[Response]`/`Option[IOException]` — eliminates null sentinel; no dead post-loop code

## Performance Metrics

| Phase | Plan | Duration | Tasks | Files |
|-------|------|----------|-------|-------|
| 01-connection-pool-config | 01-01 | 2min | 2 | 2 |
| 01-connection-pool-config | 01-02 | ~2min | 3 | 3 |
| 02-service-semaphore | 02-01 | ~3min | 1 | 1 |
| 02-service-semaphore | 02-02 | ~15min | 2 | 7 |
| 02-service-semaphore | 02-03 | ~5min | 2 | 6 |
| 03-agent-retry-with-backoff | 03-01 | 3min | 2 | 3 |
| 03-agent-retry-with-backoff | 03-02 | ~25min | 2 | 2 |

## Last Session

**Last run:** 2026-05-22  
**Stopped at:** All phases complete. Code review run; all 5 critical findings fixed. Soft-reset to single staged change set ready for user commit.

## Planning Artifacts

- `.planning/PROJECT.md` — Project context and requirements
- `.planning/REQUIREMENTS.md` — Full v1/v2 requirement list with traceability
- `.planning/ROADMAP.md` — 3-phase execution roadmap
- `.planning/config.json` — GSD workflow config (YOLO, granular, quality model)
- `.planning/codebase/` — Full codebase map (7 documents, 2026-05-21)

## Key Context for Agents

- **Server config:** `server/src/main/scala/za/co/absa/atum/server/config/PostgresConfig.scala`
- **HikariCP setup:** `server/src/main/scala/za/co/absa/atum/server/api/database/TransactorProvider.scala`
- **Server config file:** `server/src/main/resources/reference.conf`
- **Service layer:** `server/src/main/scala/za/co/absa/atum/server/api/v1/service/` and `v2/service/`
- **Agent dispatcher:** `agent/src/main/scala/za/co/absa/atum/agent/dispatcher/HttpDispatcher.scala`
- **ZIO version:** 2.0.19 — use `zio.Semaphore`, not ZIO 1.x API
- **Agent Java target:** Java 8 — no Java 11+ APIs in agent module
- **sttp on agent:** 3.5.2 (pinned for Java 8 compatibility)
