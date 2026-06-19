# atum-service

## Current State

**Last shipped:** DB Connection Bottleneck Improvements (Issue #205) — 2026-05-22
**Status:** ✅ Milestone complete. No active milestone.

### What was shipped
- HikariCP `connectionTimeout` now explicit + configurable (default 60 s)
- ZIO `Semaphore` at service layer queues burst DB requests instead of exhausting the pool
- `HttpDispatcher` retries transient 5xx/timeouts with exponential backoff + jitter (configurable)

### Next milestone goals
_Not yet defined. Run `/gsd-new-milestone` to start planning._

---

<details>
<summary>Milestone: DB Connection Bottleneck Improvements (Issue #205)</summary>



atum-service is a data quality measurement platform for Spark environments. Spark jobs instrument DataFrames via `atum-agent`, which sends checkpoints and measurements to `atum-server` (a ZIO/http4s/Tapir REST service) for storage in PostgreSQL. `atum-reader` provides read-back access to stored data.

This milestone addresses issue #205: DB connection pool exhaustion under high concurrency (100–200 Aqueduct pipeline jobs in a 10–20 second window), causing timeout errors for consumers.

## Core Value

Consumers never see timeout errors caused by DB connection pool exhaustion — the system gracefully queues, retries, and absorbs burst load.

## Requirements

### Validated

<!-- Shipped and confirmed valuable. -->

- ✓ ZIO-based REST service with Tapir/http4s — v0.7.0
- ✓ HikariCP connection pool with Prometheus metrics — v0.7.0
- ✓ Flyway-managed PostgreSQL schema — v0.7.0
- ✓ atum-agent Spark library with HTTP dispatch — v0.7.0

### Active

- [x] Add `connectionTimeout` field to `PostgresConfig` and wire into `TransactorProvider`'s HikariConfig — default 60 s, minimum 250 ms guard added
- [x] Add a ZIO `Semaphore` at the **service layer**, sized `(maxPoolSize - 1).max(1)` permits, to queue concurrent DB requests before they hit HikariCP
- [x] Add exponential-backoff-with-jitter retry logic to `HttpDispatcher` in `atum-agent`, configurable via `atum.dispatcher.http.retry.*` with sensible defaults

### Out of Scope

- Increasing Aurora ACU or DB pool tuning in deployment config — quick win already done, not a code change
- Retry logic at the server side — handled by the Semaphore queue instead
- Changing the `fa-db` / Doobie abstraction layer — too disruptive, not needed

## Context

- **Issue:** [#205 — Resource Bottlenecks: DB connection pool exhaustion](https://github.com/absa-group/atum-service-deployment/issues/205)
- **Root cause:** Aqueduct fires 100–200 Spark jobs in 10–20 second windows. Each job calls `createPartitioning` → `saveCheckpoint`, consuming HikariCP connections. Default `connectionTimeout` (30s) fails fast; no application-level queueing exists; agent has no retry on failure.
- **Current `PostgresConfig`:** `minimumIdle=4`, `maxPoolSize=10`, no `connectionTimeout` field
- **`HttpDispatcher`:** Uses sttp `3.5.2` + OkHttp sync backend; no retry logic; single-shot per HTTP call
- **Service layer pattern:** ZIO effects, `ZLayer`/`ZIO.service` DI; service methods are good candidates for Semaphore wrapping

## Constraints

- **Java 8**: `atum-agent` cross-compiles to Java 8 — retry implementation must not use Java 11+ APIs or libraries incompatible with Java 8
- **sttp 3.5.2**: Agent is pinned to sttp `3.5.2` (last version supporting Java 8) — retry must work within this version
- **Non-breaking API**: Agent config changes must be backward-compatible (new retry fields optional with defaults)
- **ZIO 2.x**: Server uses ZIO 2.0.19; Semaphore must use `zio.Semaphore` (not ZIO 1.x API)

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Semaphore at service layer | Centralised per-service, avoids cross-cutting concerns at transactor level; service layer already has ZIO context | ✅ `SemaphoreProvider.layer` wired in `Main.scala`; `(maxPoolSize-1).max(1)` formula prevents 0-permit deadlock |
| Exponential backoff with jitter on agent | Prevents thundering herd on retry storms; jitter spreads load | ✅ `computeDelay` uses `min(initialDelay * 2^attempt, maxDelay) + ≤10% jitter`; overflow guard via pre-division check |
| Retry config at AtumAgent public API | Allows consumers to tune for their SLA without code changes | ✅ `atum.dispatcher.http.retry.*` HOCON keys, optional with `hasPath` guards; `require()` validates non-negative values |
| `connectionTimeout` default 60 s | Gives HikariCP time to wait for a free connection during burst rather than failing immediately at 30 s default | ✅ `connectionTimeout=60000` in `reference.conf`; `require(>= 250L)` guard in `TransactorProvider` |

---

## Evolution

After each phase transition:
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

---
*Last updated: 2026-05-22 — all phases complete; decisions finalised*

</details>
