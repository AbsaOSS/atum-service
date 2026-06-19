<!-- refreshed: 2026-05-21 -->
# Architecture

**Analysis Date:** 2026-05-21

## System Overview

```text
┌────────────────────────────────────────────────────────────────────────────┐
│                         Client / Spark Applications                        │
│      atum-agent (Spark lib)               atum-reader (read-only lib)      │
│  `agent/src/main/scala/...agent/`     `reader/src/main/scala/...reader/`   │
└───────────────────────────┬────────────────────────┬───────────────────────┘
                            │  HTTP REST (sttp)       │  HTTP REST (sttp)
                            ▼                         ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                        atum-server  (ZIO HTTP service)                     │
│                  `server/src/main/scala/za/co/absa/atum/server/`           │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────────────┐  │
│  │   HTTP Layer     │  │  API v1 & v2     │  │   Common / Infra         │  │
│  │  (Tapir+http4s)  │  │  Endpoints       │  │  Health, Metrics, SSL    │  │
│  │  `api/common/    │  │  `api/v1/http/`  │  │  `api/common/http/`      │  │
│  │   http/`         │  │  `api/v2/http/`  │  │                          │  │
│  └────────┬─────────┘  └──────────────────┘  └──────────────────────────┘  │
│           │                                                                 │
│  ┌────────▼─────────────────────────────────────────────────────────────┐   │
│  │                       Controller Layer                               │   │
│  │  `api/v1/controller/`   `api/v2/controller/`   `api/common/         │   │
│  │                                                  controller/`        │   │
│  └────────┬─────────────────────────────────────────────────────────────┘   │
│           │                                                                 │
│  ┌────────▼─────────────────────────────────────────────────────────────┐   │
│  │                         Service Layer                                │   │
│  │  `api/v1/service/`              `api/v2/service/`                   │   │
│  └────────┬─────────────────────────────────────────────────────────────┘   │
│           │                                                                 │
│  ┌────────▼─────────────────────────────────────────────────────────────┐   │
│  │                       Repository Layer                               │   │
│  │  `api/v1/repository/`           `api/v2/repository/`                │   │
│  └────────┬─────────────────────────────────────────────────────────────┘   │
│           │                                                                 │
│  ┌────────▼─────────────────────────────────────────────────────────────┐   │
│  │                   Database Function Layer                            │   │
│  │  `api/database/runs/functions/`  `api/database/flows/functions/`    │   │
│  │  (fa-db / Doobie wrappers for Postgres stored functions)            │   │
│  └────────┬─────────────────────────────────────────────────────────────┘   │
└───────────┼─────────────────────────────────────────────────────────────────┘
            │ JDBC (Doobie / HikariCP)
            ▼
┌────────────────────────────────────────────────────────────────────────────┐
│              PostgreSQL  (atum_db)                                         │
│  Flyway-managed schema: `database/src/main/postgres/`                      │
│  Schemas: runs / flows / validation / public                               │
└────────────────────────────────────────────────────────────────────────────┘

Shared Data Contract:
  atum-model  `model/src/main/scala/za/co/absa/atum/model/`
  (DTOs, envelopes, ApiPaths — used by agent, reader, and server)
```

## Component Responsibilities

| Component | Responsibility | Root Path |
|-----------|----------------|-----------|
| `atum-model` | Shared DTOs, envelopes, API path constants | `model/src/main/scala/za/co/absa/atum/model/` |
| `atum-agent` | Spark-side library; measures DataFrames & sends data to server | `agent/src/main/scala/za/co/absa/atum/agent/` |
| `atum-reader` | Read-only client library for querying stored atum data | `reader/src/main/scala/za/co/absa/atum/reader/` |
| `atum-server` | ZIO-based REST service; receives, stores, and serves atum data | `server/src/main/scala/za/co/absa/atum/server/` |
| `atum-database` | Flyway migration scripts and Postgres DDL/SQL | `database/src/main/postgres/` |

## Pattern Overview

**Overall:** Multi-module SBT project following a layered, effect-typed architecture (ZIO on the server, plain Scala on clients).

**Key Characteristics:**
- Server uses **ZIO** for all effects; dependency injection via `ZLayer`/`ZIO.service`
- Server layers are strictly separated: HTTP → Controller → Service → Repository → DB Functions
- **Tapir** defines typed API endpoints; **http4s + BlazeServer** serves them
- Database access uses **fa-db (ABSA's FunctionAsADatabase)** + **Doobie** — every DB operation maps to a Postgres stored function/procedure (not raw queries in Scala)
- API is versioned (v1 legacy, v2 current) with dedicated controller/service/repository packages per version
- Clients (agent, reader) use **sttp** HTTP client with the shared `model` module for request/response types
- Configuration via **Typesafe Config** (`reference.conf`) with ZIO config integration

## Layers

**HTTP / Endpoint Layer:**
- Purpose: Define typed Tapir endpoints, wire them to ZServerEndpoints, assemble HTTP routes, serve Swagger UI
- Location: `server/src/main/scala/za/co/absa/atum/server/api/v1/http/Endpoints.scala`, `server/src/main/scala/za/co/absa/atum/server/api/v2/http/Endpoints.scala`, `server/src/main/scala/za/co/absa/atum/server/api/common/http/`
- Contains: `Endpoints` objects, `Routes`, `Server`, `BaseEndpoints`, monitoring routes, SSL
- Depends on: Controller layer (ZIO services injected via environment)
- Used by: `Server.scala` / Blaze HTTP server

**Controller Layer:**
- Purpose: Bridge between HTTP and business logic; maps `ServiceError` to `ErrorResponse`, wraps results in response envelopes
- Location: `server/src/main/scala/za/co/absa/atum/server/api/v1/controller/`, `server/src/main/scala/za/co/absa/atum/server/api/v2/controller/`, `server/src/main/scala/za/co/absa/atum/server/api/common/controller/BaseController.scala`
- Contains: Trait + `Impl` class pairs; `BaseController` with `serviceCall`, `mapToSingleSuccessResponse`, `mapToMultiSuccessResponse`, `mapToPaginatedResponse`
- Depends on: Service layer
- Used by: HTTP Endpoints layer

**Service Layer:**
- Purpose: Business logic orchestration; maps `DatabaseError` to `ServiceError`
- Location: `server/src/main/scala/za/co/absa/atum/server/api/v1/service/`, `server/src/main/scala/za/co/absa/atum/server/api/v2/service/`
- Contains: Trait + `Impl` class pairs; `BaseService` with `repositoryCall`
- Depends on: Repository layer
- Used by: Controller layer

**Repository Layer:**
- Purpose: Data access abstraction; calls DB functions, converts DB model objects to DTOs, handles pagination
- Location: `server/src/main/scala/za/co/absa/atum/server/api/v1/repository/`, `server/src/main/scala/za/co/absa/atum/server/api/v2/repository/`
- Contains: Trait + `Impl` class pairs; `BaseRepository` with `dbSingleResultCallWithStatus`, `dbMultipleResultCallWithAggregatedStatus`
- Depends on: Database function layer
- Used by: Service layer

**Database Function Layer:**
- Purpose: Wrap individual Postgres stored functions using fa-db + Doobie; each class = one stored procedure
- Location: `server/src/main/scala/za/co/absa/atum/server/api/database/runs/functions/`, `server/src/main/scala/za/co/absa/atum/server/api/database/flows/functions/`
- Contains: `WriteCheckpointV2`, `CreatePartitioning`, `GetPartitioningCheckpoints`, etc. — each extends `DoobieSingleResultFunctionWithStatus` or `DoobieMultipleResultFunctionWithStatus`
- Depends on: `PostgresDatabaseProvider` → `DoobieEngine` → Doobie `Transactor`
- Used by: Repository layer

**Model Module:**
- Purpose: Shared contract between server and clients
- Location: `model/src/main/scala/za/co/absa/atum/model/`
- Contains: `dto/` (request/response DTOs), `envelopes/` (`ResponseEnvelope`, `SuccessResponse`, `ErrorResponse`, `Pagination`), `types/`, `ApiPaths`, `ResultValueType`
- Depends on: Nothing (standalone)
- Used by: server, agent, reader

**Agent Module:**
- Purpose: Spark application library; measures DataFrames, manages `AtumContext` per partitioning, dispatches data to server
- Location: `agent/src/main/scala/za/co/absa/atum/agent/`
- Contains: `AtumAgent` (singleton object), `AtumContext`, `Dispatcher` hierarchy (`HttpDispatcher`, `ConsoleDispatcher`, `CapturingDispatcher`), `MeasurementProcessor`
- Depends on: `model` module, sttp, Spark
- Used by: Customer Spark applications

**Reader Module:**
- Purpose: Read-only client library for consuming stored atum measurements
- Location: `reader/src/main/scala/za/co/absa/atum/reader/`
- Contains: `PartitioningReader[F]`, `FlowReader[F]`, abstract `Reader[F]`, `PartitioningIdProvider`, `ServerConfig`
- Depends on: `model` module, sttp (polymorphic in effect type `F[_]`)
- Used by: Customer applications that query atum data

## Data Flow

### Agent: Write Checkpoint (Primary Ingest Path)

1. Spark app calls `atumContext.saveCheckpoint(...)` (`agent/src/main/scala/za/co/absa/atum/agent/AtumContext.scala`)
2. `AtumAgent.saveCheckpoint()` delegates to `Dispatcher.saveCheckpoint()` (`agent/src/main/scala/za/co/absa/atum/agent/AtumAgent.scala`)
3. `HttpDispatcher.saveCheckpoint()` resolves `partitioningId` via `GET /api/v2/partitionings?partitioning=<base64>`, then `POST /api/v2/partitionings/{id}/checkpoints` (`agent/src/main/scala/za/co/absa/atum/agent/dispatcher/HttpDispatcher.scala`)
4. Server `Endpoints.postCheckpointEndpoint` (v2) receives request → `CheckpointController.postCheckpoint()` (`server/src/main/scala/za/co/absa/atum/server/api/v2/http/Endpoints.scala`)
5. `CheckpointControllerImpl.postCheckpoint()` calls `CheckpointService.saveCheckpoint()` (`server/src/main/scala/za/co/absa/atum/server/api/v2/controller/CheckpointControllerImpl.scala`)
6. `CheckpointServiceImpl` calls `CheckpointRepository.writeCheckpoint()` (`server/src/main/scala/za/co/absa/atum/server/api/v2/service/CheckpointServiceImpl.scala`)
7. `CheckpointRepositoryImpl` invokes `WriteCheckpointV2` DB function (`server/src/main/scala/za/co/absa/atum/server/api/v2/repository/CheckpointRepositoryImpl.scala`)
8. `WriteCheckpointV2` executes `runs.write_checkpoint(...)` Postgres stored function via Doobie (`server/src/main/scala/za/co/absa/atum/server/api/database/runs/functions/WriteCheckpointV2.scala`)
9. Data persisted in PostgreSQL `runs` schema

### Reader: Read Checkpoints Path

1. App creates `PartitioningReader[F](partitioning)` (`reader/src/main/scala/za/co/absa/atum/reader/PartitioningReader.scala`)
2. `getCheckpointsPage()` first resolves partitioning ID via `GET /api/v2/partitionings?partitioning=<base64>` then queries `GET /api/v2/partitionings/{id}/checkpoints`
3. Server routes → `CheckpointController.getPartitioningCheckpoints()` → `CheckpointService` → `CheckpointRepository` → `GetPartitioningCheckpoints` DB function

### Agent: Create Partitioning

1. Spark app calls `AtumAgent.getOrCreateAtumContext(atumPartitions)` (`agent/src/main/scala/za/co/absa/atum/agent/AtumAgent.scala`)
2. `HttpDispatcher.createPartitioning()` first checks if partitioning already exists via GET, then POSTs to `/api/v2/partitionings` if new
3. Fetches measures + additional data for the partitioning to hydrate `AtumContextDTO`
4. Returns `AtumContext` instance with all measures and metadata

**State Management:**
- Server is stateless; all state in PostgreSQL
- Agent holds an in-memory `Map[AtumPartitions, AtumContext]` in `AtumAgent` (synchronized mutable map) — one per JVM

## Key Abstractions

**`Dispatcher`:**
- Purpose: Abstract transport layer for the agent — pluggable output target
- Examples: `agent/src/main/scala/za/co/absa/atum/agent/dispatcher/HttpDispatcher.scala`, `ConsoleDispatcher.scala`, `CapturingDispatcher.scala`
- Pattern: Abstract class with `Config` constructor; factory in `AtumAgent.dispatcherFromConfig()`

**`BaseController` / `BaseService` / `BaseRepository`:**
- Purpose: Error transformation between layers (DB error → Service error → HTTP error response)
- Files: `server/src/main/scala/za/co/absa/atum/server/api/common/controller/BaseController.scala`, `server/src/main/scala/za/co/absa/atum/server/api/common/service/BaseService.scala`, `server/src/main/scala/za/co/absa/atum/server/api/common/repository/BaseRepository.scala`
- Pattern: Trait mixin; each layer wraps the call in its error-mapping helper (`serviceCall`, `repositoryCall`, `dbSingleResultCallWithStatus`)

**`DoobieSingleResultFunctionWithStatus` / `DoobieMultipleResultFunctionWithStatus` (fa-db):**
- Purpose: Each subclass represents exactly one Postgres stored function call
- Examples: `WriteCheckpointV2`, `CreatePartitioning`, `GetPartitioningCheckpoints` in `server/src/main/scala/za/co/absa/atum/server/api/database/runs/functions/`
- Pattern: Class extends fa-db base class, provides SQL fragment args; static `val layer` exposes as ZIO `URLayer`

**`ResponseEnvelope` / `SuccessResponse` / `ErrorResponse`:**
- Purpose: Typed HTTP response wrappers for all API responses
- Files: `model/src/main/scala/za/co/absa/atum/model/envelopes/`
- Pattern: `SingleSuccessResponse[A]`, `MultiSuccessResponse[A]`, `PaginatedResponse[A]` for successes; `ConflictErrorResponse`, `NotFoundErrorResponse`, etc. for errors

**`Reader[F[_]]`:**
- Purpose: Polymorphic base class for read-only clients — works with any monad (Future, ZIO Task, cats-effect IO)
- File: `reader/src/main/scala/za/co/absa/atum/reader/core/Reader.scala`
- Pattern: Abstract class parameterised on effect type `F[_]`; requires implicit `SttpBackend[F, Any]` and `MonadError[F]`

## Entry Points

**Server:**
- Location: `server/src/main/scala/za/co/absa/atum/server/Main.scala`
- Triggers: JVM startup; `ZIOAppDefault.run`
- Responsibilities: Assembles the full ZLayer dependency graph (controllers → services → repositories → DB functions → DB provider), starts Blaze HTTP server on port 8080 (HTTP) or 8443 (HTTPS)

**Agent (library):**
- Location: `agent/src/main/scala/za/co/absa/atum/agent/AtumAgent.scala` — `object AtumAgent`
- Triggers: Called by Spark application code
- Responsibilities: Singleton entry point; reads `atum.dispatcher.type` from Typesafe Config and instantiates appropriate `Dispatcher`; manages `AtumContext` lifecycle

**Reader (library):**
- Location: `reader/src/main/scala/za/co/absa/atum/reader/PartitioningReader.scala`, `reader/src/main/scala/za/co/absa/atum/reader/FlowReader.scala`
- Triggers: Instantiated by consuming application
- Responsibilities: Provide typed, effect-polymorphic API for reading partitionings, checkpoints, additional data

## Architectural Constraints

- **Threading:** Server runs on ZIO fiber-based concurrency. Agent uses synchronous blocking sttp/OkHttp (`OkHttpSyncBackend`) — each agent call blocks the calling thread. Reader is effect-polymorphic (non-blocking when using async backend).
- **Global state:** `object AtumAgent` is a JVM singleton that holds a mutable `Map[AtumPartitions, AtumContext]` protected by `synchronized`. This is the only mutable global state.
- **DB access model:** All database operations go through Postgres stored functions via fa-db. No ad-hoc SQL is written in Scala; all SQL lives in `database/src/main/postgres/`. This is a hard architectural constraint.
- **API versioning:** v1 endpoints (`/api/v1/createCheckpoint`, `/api/v1/createPartitioning`) use a different DTO shape and are maintained for backward compatibility. v2 is the current target. New development goes in v2.
- **Schema management:** Flyway manages the Postgres schema; migration scripts are in `database/src/main/postgres/` using version prefix naming (e.g., `V0.5.1.3__...`). The `database` module is a separate SBT module only compiled with Java ≥ recommended version.

## Anti-Patterns

### Mutable synchronized map in agent singleton

**What happens:** `object AtumAgent` holds `private[this] var contexts: Map[AtumPartitions, AtumContext]` protected by `synchronized {}` (`agent/src/main/scala/za/co/absa/atum/agent/AtumAgent.scala`)
**Why it's wrong:** Global mutable state in a singleton creates concurrency risks in multi-threaded Spark executors; the synchronization is on `this` (the object itself), which can become a bottleneck.
**Do this instead:** Use a `ConcurrentHashMap` or ensure the map lives in a thread-safe immutable context per partition; or redesign to not cache contexts globally.

### Debug `println` in BaseController

**What happens:** `BaseController.serviceCall()` calls `ZIO.succeed(println(res))` on every successful response (`server/src/main/scala/za/co/absa/atum/server/api/common/controller/BaseController.scala`, line 44)
**Why it's wrong:** `println` bypasses the structured SLF4J logging configured in `Main.scala`; produces unstructured stdout noise in production.
**Do this instead:** Replace with `ZIO.logDebug(res.toString)` or remove entirely.

## Error Handling

**Strategy:** Typed error channels at every layer boundary using sealed traits.

**Patterns:**
- DB layer returns `IO[DatabaseError, A]` — variants: `GeneralDatabaseError`, `ConflictDatabaseError`, `NotFoundDatabaseError`, `ErrorInDataDatabaseError` (`server/src/main/scala/za/co/absa/atum/server/api/exception/DatabaseError.scala`)
- Service layer maps `DatabaseError` → `ServiceError` via `BaseService.repositoryCall()` (`server/src/main/scala/za/co/absa/atum/server/api/common/service/BaseService.scala`)
- Controller layer maps `ServiceError` → `ErrorResponse` (HTTP status-coded response) via `BaseController.serviceCall()` (`server/src/main/scala/za/co/absa/atum/server/api/common/controller/BaseController.scala`)
- fa-db `StatusException` subtypes (`DataConflictException`, `DataNotFoundException`, `ErrorInDataException`) from Postgres function status codes are translated in `BaseRepository`

## Cross-Cutting Concerns

**Logging:** SLF4J backend via ZIO logging (`zio-logging-slf4j`). Configured in `Main.bootstrap`. Log config in `server/src/main/resources/logback.xml`. Repository layer logs DB operation results at DEBUG/ERROR level.
**Validation:** Input validation on pagination params via Tapir `Validator.inRange` / `Validator.min` in endpoint definitions. Partitioning JSON encoded as base64 in query params, decoded in controller layer.
**Authentication:** No authentication on API endpoints (all `PublicEndpoint`). Auth may be handled at the infrastructure level (e.g., network/proxy).
**Metrics:** Prometheus metrics exposed via Tapir/ZIO metrics integration; JVM metrics, HTTP request metrics, Hikari pool metrics — all configurable in `reference.conf` under `monitoring`.
**Configuration:** Typesafe Config (`reference.conf` in server resources) with ZIO config derivation (`ZIO.config[T]`). Postgres password can be fetched from AWS Secrets Manager (`PostgresDataSourceWithPasswordFromSecretsManager`).

---

*Architecture analysis: 2026-05-21*
