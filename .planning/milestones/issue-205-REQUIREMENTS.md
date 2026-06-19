# Requirements Archive: DB Connection Bottleneck Improvements (Issue #205)

**Archived:** 2026-05-22
**Status:** ✅ All v1 requirements shipped
**Core Value:** Consumers never see timeout errors caused by DB connection pool exhaustion under burst load

## v1 Requirements — Final Status

### Server — Connection Pool Configuration

- [x] **POOL-01**: `PostgresConfig` case class includes a `connectionTimeout` field (`Long`) — ✅ Validated (plan 01-01)
- [x] **POOL-02**: `TransactorProvider` sets `hikariConfig.setConnectionTimeout(postgresConfig.connectionTimeout)` — ✅ Validated (plan 01-01)
- [x] **POOL-03**: `reference.conf` includes `connectionTimeout` with a default of 60000 ms (60 s) and a descriptive comment — ✅ Validated (plan 01-02)
- [x] **POOL-04**: `PostgresConfig` is covered by unit tests verifying the new field is parsed from config — ✅ Validated (plan 01-02)

### Server — Service Layer Semaphore

- [x] **SEM-01**: A `ZIO.Semaphore` is created at server startup, sized to `maxPoolSize - 1` (or a configurable value below `maxPoolSize`) — ✅ Validated (plan 02-01)
- [x] **SEM-02**: The Semaphore is provided as a `ZLayer` dependency to all service classes that perform DB operations — ✅ Validated (plan 02-02)
- [x] **SEM-03**: Every service method that triggers a DB call acquires one Semaphore permit for its duration — ✅ Validated (plan 02-02)
- [x] **SEM-04**: Semaphore acquisition is transparent to callers — failures/timeouts surface as the same error types as before — ✅ Validated (plan 02-02)
- [x] **SEM-05**: Unit tests cover that concurrent requests above the Semaphore limit are queued, not rejected — ✅ Validated (plan 02-03)

### Agent — Retry Logic

- [x] **RETRY-01**: `HttpDispatcher` retries failed HTTP requests using exponential backoff with jitter — ✅ Validated (plan 03-02)
- [x] **RETRY-02**: Retry behaviour is configurable: `maxRetries` (default 3), `initialDelay` (default 500 ms), `maxDelay` (default 10 s) — ✅ Validated (plan 03-01)
- [x] **RETRY-03**: Retry config is exposed at the `AtumAgent` / public config level (`atum.dispatcher.http.retry.*`) — ✅ Validated (plan 03-01)
- [x] **RETRY-04**: Retry config fields are optional with defaults — existing agent configurations remain valid — ✅ Validated (plan 03-01)
- [x] **RETRY-05**: Only transient errors are retried (HTTP 5xx, connection timeout); client errors (4xx) are not retried — ✅ Validated (plan 03-02)
- [x] **RETRY-06**: Unit tests cover retry behaviour: retries on transient errors, no retry on 4xx, respects `maxRetries` limit — ✅ Validated (plan 03-02)
- [x] **RETRY-07**: Retry implementation is compatible with Java 8 (no Java 11+ APIs) — ✅ Validated (plan 03-02)

## v2 Requirements — Deferred to Next Milestone

### Observability

- **OBS-01**: Semaphore queue depth exposed as a Prometheus metric
- **OBS-02**: Retry attempts logged at DEBUG level with attempt count and delay

### Enhanced Resilience

- **RES-01**: Circuit breaker on agent side to stop retrying when server is completely unavailable
- **RES-02**: Server-side rate limiting per client to prevent a single agent from monopolising connections

## Out of Scope

| Feature | Reason |
|---------|--------|
| Aurora/RDS config tuning | Deployment concern, not a code change |
| Server-side retry | Semaphore queueing is the server-side protection mechanism |
| Changing fa-db / Doobie abstraction | Too disruptive, not required to solve the problem |
| Repository-level Semaphore | Service layer is the right boundary; avoids per-function duplication |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| POOL-01 | Phase 1 | ✅ Complete (plan 01-01) |
| POOL-02 | Phase 1 | ✅ Complete (plan 01-01) |
| POOL-03 | Phase 1 | ✅ Complete (plan 01-02) |
| POOL-04 | Phase 1 | ✅ Complete (plan 01-02) |
| SEM-01 | Phase 2 | ✅ Complete (plan 02-01) |
| SEM-02 | Phase 2 | ✅ Complete (plan 02-02) |
| SEM-03 | Phase 2 | ✅ Complete (plan 02-02) |
| SEM-04 | Phase 2 | ✅ Complete (plan 02-02) |
| SEM-05 | Phase 2 | ✅ Complete (plan 02-03) |
| RETRY-01 | Phase 3 | ✅ Complete (plan 03-02) |
| RETRY-02 | Phase 3 | ✅ Complete (plan 03-01) |
| RETRY-03 | Phase 3 | ✅ Complete (plan 03-01) |
| RETRY-04 | Phase 3 | ✅ Complete (plan 03-01) |
| RETRY-05 | Phase 3 | ✅ Complete (plan 03-02) |
| RETRY-06 | Phase 3 | ✅ Complete (plan 03-02) |
| RETRY-07 | Phase 3 | ✅ Complete (plan 03-02) |

**Coverage:** v1 requirements: 16/16 ✅ | Unmapped: 0

---
*Requirements defined: 2026-05-21 | Archived: 2026-05-22 — all v1 requirements complete*
