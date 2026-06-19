# Milestone: DB Connection Bottleneck Improvements (Issue #205)

**Status:** ✅ SHIPPED 2026-05-22
**Phases:** 1–3
**Total Plans:** 7
**Issue:** [#205](https://github.com/absa-group/atum-service-deployment/issues/205)
**Branch:** feature/205-db-connection-bottlenecks-improvements

## Overview

Three targeted improvements eliminate DB connection pool exhaustion errors under burst load.
Phase 1 makes the HikariCP connection timeout explicit and configurable so operators can tune
behaviour without code changes. Phase 2 installs a ZIO Semaphore gate at the service layer so
the server never saturates its own pool — excess requests queue transparently instead of racing
to exhaustion. Phase 3 adds exponential-backoff retry logic to the agent so transient 5xx
responses caused by any remaining back-pressure are automatically recovered, making the full
path resilient end-to-end.

## Phases

### Phase 1: Connection Pool Config

**Goal**: Operators can control HikariCP connection-timeout behaviour through config, and a safe 60 s default ships out-of-the-box
**Depends on**: Nothing (first phase)
**Requirements**: POOL-01, POOL-02, POOL-03, POOL-04
**Scope**: S
**Plans**: 2 plans

Plans:
- [x] 01-01: Add `connectionTimeout` field to `PostgresConfig` and wire into `TransactorProvider`
- [x] 01-02: Add default + comment to `reference.conf`; write unit tests for config parsing

**Success Criteria:**
1. `PostgresConfig` compiles with the new `connectionTimeout: Long` field and the unit test suite parses it from a HOCON snippet without error
2. Starting the server with no custom config results in a HikariCP pool configured with a 60 000 ms connection timeout (verifiable via HikariCP startup log line)
3. Overriding `connectionTimeout` in `application.conf` is picked up at runtime without any code change
4. CI passes — no existing config tests regress

### Phase 2: Service Layer Semaphore

**Goal**: Concurrent DB-bound requests are transparently queued at the service layer so the connection pool is never over-subscribed
**Depends on**: Phase 1
**Requirements**: SEM-01, SEM-02, SEM-03, SEM-04, SEM-05
**Scope**: M
**Plans**: 3 plans

Plans:
- [x] 02-01: Create `SemaphoreProvider` ZLayer sized `(maxPoolSize - 1).max(1)`
- [x] 02-02: Wrap all 17 `repositoryCall(...)` sites with `semaphore.withPermit`; wire into `Main.scala`
- [x] 02-03: Fix 5 service test `.provide()` blocks; add `SemaphoreQueuingUnitTests` (SEM-05)

**Success Criteria:**
1. The server starts and a single `ZIO.Semaphore` (sized `maxPoolSize - 1`) is visible in the dependency graph via `ZLayer` wiring
2. Every v1 and v2 service method that calls the DB acquires one Semaphore permit; a permit is always released regardless of success or failure
3. When N concurrent requests exceed the Semaphore limit, the excess requests block (queue) rather than proceed — verified by a unit test that asserts no request is rejected and all eventually complete
4. Error types returned to callers are unchanged — the Semaphore wrapper does not introduce new error channels
5. CI passes — all existing service-layer tests continue to compile and pass with the new `ZLayer` dependency injected

### Phase 3: Agent Retry with Backoff

**Goal**: The agent automatically recovers from transient server-side errors using exponential backoff, without requiring consumer code changes
**Depends on**: Phase 2
**Requirements**: RETRY-01, RETRY-02, RETRY-03, RETRY-04, RETRY-05, RETRY-06, RETRY-07
**Scope**: M
**Plans**: 2 plans

Plans:
- [x] 03-01: Create `RetryConfig` case class + document HOCON retry keys in both `reference.conf` files
- [x] 03-02: Add `withRetry` loop to `HttpDispatcher` (all 7 call sites); write `HttpDispatcherRetryUnitTests`

**Success Criteria:**
1. A 5xx response or connection timeout causes `HttpDispatcher` to retry up to `maxRetries` times with exponentially increasing delays (+ jitter), then fail — verifiable via unit test with a mock server
2. A 4xx response causes `HttpDispatcher` to fail immediately without any retry — verifiable via unit test
3. Retry parameters (`maxRetries`, `initialDelay`, `maxDelay`) are configurable under `atum.dispatcher.http.retry.*`; omitting the block entirely leaves defaults intact (no `ConfigException` thrown)
4. The compiled agent artifact runs on a Java 8 JVM — CI uses a Java 8 target for the agent module and the build succeeds
5. An existing `AtumAgent` config file with no `retry` block is accepted without error — backward compatibility confirmed by a test loading the legacy config shape

---

## Milestone Summary

**Key Decisions:**
- Use `Long` not `Int` for `connectionTimeout` — matches HikariCP `setConnectionTimeout(long)` JVM primitive; avoids silent truncation above `Int.MaxValue` (~2.1B ms)
- Compile gate deferred to Plan 01-02: compiling with new field but no HOCON key throws `ConfigException` at ZIO config derivation
- Used `config.hasPath` guards per-key in `RetryConfig.fromConfig` — prevents `ConfigException` when retry block absent; enables partial overrides
- Use `Answer<T>` (throws Throwable) instead of `thenThrow` for IOException mocking — Mockito rejects checked exceptions on non-declaring interface methods
- `withRetry` rewritten with `Option[Response]`/`Option[IOException]` — eliminates null sentinel; no dead post-loop code
- Semaphore at service layer (not transactor level) — centralised per-service, avoids cross-cutting concerns
- Exponential backoff with jitter on agent — prevents thundering herd; jitter spreads load

**Issues Resolved:**
- DB connection pool exhaustion under burst load (100–200 Aqueduct jobs in 10–20 s windows)
- Agent fails permanently on transient server-side errors (now retries with backoff)
- No operator-tunable connection timeout (now configurable via HOCON)

**Issues Deferred (v2 backlog):**
- OBS-01: Semaphore queue depth exposed as Prometheus metric
- OBS-02: Retry attempts logged at DEBUG level with attempt count and delay
- RES-01: Circuit breaker on agent side
- RES-02: Server-side rate limiting per client

**Technical Debt Incurred:**
- None significant; all implementation decisions were deliberate and documented

---

**Files changed since v0.7.0:** 21 files, +680/-51 LOC

_For current project status, see .planning/ROADMAP.md_
