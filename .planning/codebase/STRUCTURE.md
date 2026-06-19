# Codebase Structure

**Analysis Date:** 2026-05-21

## Directory Layout

```
atum-service/
├── model/                        # Shared data contract (DTOs, envelopes, API paths)
│   └── src/
│       ├── main/scala/za/co/absa/atum/model/
│       │   ├── dto/              # Request/response data transfer objects
│       │   ├── envelopes/        # Response wrappers (SuccessResponse, ErrorResponse, Pagination)
│       │   ├── types/            # Basic type aliases (AtumPartitions, etc.)
│       │   ├── utils/            # JsonSyntaxExtensions
│       │   ├── ApiPaths.scala    # Centralized API path constants
│       │   └── ResultValueType.scala
│       └── test/scala/...
│
├── agent/                        # Spark-side ingest client library
│   └── src/
│       ├── main/scala/za/co/absa/atum/agent/
│       │   ├── AtumAgent.scala   # Singleton entry point, context registry
│       │   ├── AtumContext.scala # Per-partitioning measurement context
│       │   ├── core/             # MeasurementProcessor
│       │   ├── dispatcher/       # Dispatcher trait + HttpDispatcher, ConsoleDispatcher, CapturingDispatcher
│       │   ├── exception/        # AtumAgentException
│       │   └── model/            # Measure, MeasureResult, MeasurementBuilder, MeasuresBuilder
│       └── test/scala/...
│
├── reader/                       # Read-only client library (effect-polymorphic)
│   └── src/
│       ├── main/scala/za/co/absa/atum/reader/
│       │   ├── PartitioningReader.scala  # Main read API for partitioning data
│       │   ├── FlowReader.scala          # Read API for flow data
│       │   ├── core/             # Reader[F], PartitioningIdProvider, RequestResult
│       │   ├── exceptions/       # ReaderException, RequestException
│       │   ├── implicits/        # future.scala, io.scala (backend/monad instances)
│       │   ├── requests/         # QueryParamNames
│       │   └── server/           # ServerConfig
│       └── test/scala/...
│
├── server/                       # ZIO REST service application
│   └── src/
│       ├── main/
│       │   ├── resources/
│       │   │   ├── reference.conf          # Application configuration defaults
│       │   │   └── logback.xml             # Logging configuration
│       │   └── scala/za/co/absa/atum/server/
│       │       ├── Main.scala              # Application entry point; ZLayer wiring
│       │       ├── api/
│       │       │   ├── common/
│       │       │   │   ├── controller/     # BaseController trait
│       │       │   │   ├── http/           # BaseEndpoints, Endpoints (health/metrics/build-info),
│       │       │   │   │                   #   Routes, Server, ServerOptions, ServerUtils,
│       │       │   │   │                   #   HttpEnv, HttpMetrics, HikariMetrics, SSL
│       │       │   │   ├── repository/     # BaseRepository trait, CheckpointPropertiesEnricher
│       │       │   │   └── service/        # BaseService trait
│       │       │   ├── database/
│       │       │   │   ├── runs/
│       │       │   │   │   ├── Runs.scala  # DBSchema object for `runs` schema
│       │       │   │   │   └── functions/  # One class per Postgres stored function (WriteCheckpointV2,
│       │       │   │   │                   #   CreatePartitioning, GetPartitioningCheckpoints, etc.)
│       │       │   │   ├── flows/
│       │       │   │   │   ├── Flows.scala # DBSchema object for `flows` schema
│       │       │   │   │   └── functions/  # GetFlowCheckpoints, GetFlowPartitionings
│       │       │   │   ├── PostgresDatabaseProvider.scala  # Wraps DoobieEngine ZLayer
│       │       │   │   ├── PostgresDataSourceWithPasswordFromSecretsManager.scala
│       │       │   │   ├── TransactorProvider.scala        # Doobie Transactor ZLayer (HikariCP)
│       │       │   │   ├── AWSSDKs.scala                  # AWS SDK initialization
│       │       │   │   └── DoobieImplicits.scala           # Doobie type mappings
│       │       │   ├── exception/
│       │       │   │   ├── AppError.scala       # Abstract base exception
│       │       │   │   ├── DatabaseError.scala  # Sealed trait (General/Conflict/NotFound/ErrorInData)
│       │       │   │   └── ServiceError.scala   # Sealed trait (General/Conflict/NotFound/ErrorInData)
│       │       │   ├── v1/
│       │       │   │   ├── controller/     # CheckpointController + Impl, PartitioningController + Impl
│       │       │   │   ├── http/           # Endpoints.scala (createCheckpoint, createPartitioning)
│       │       │   │   ├── repository/     # CheckpointRepository + Impl, PartitioningRepository + Impl
│       │       │   │   └── service/        # CheckpointService + Impl, PartitioningService + Impl
│       │       │   └── v2/
│       │       │       ├── controller/     # CheckpointController + Impl, FlowController + Impl,
│       │       │       │                   #   PartitioningController + Impl
│       │       │       ├── http/           # Endpoints.scala (all v2 endpoints + server endpoints wiring)
│       │       │       ├── repository/     # CheckpointRepository + Impl, FlowRepository + Impl,
│       │       │       │                   #   PartitioningRepository + Impl
│       │       │       └── service/        # CheckpointService + Impl, FlowService + Impl,
│       │       │                           #   PartitioningService + Impl
│       │       ├── config/         # HikariMonitoringConfig, HttpMonitoringConfig, JvmMonitoringConfig,
│       │       │                   #   PostgresConfig, SslConfig
│       │       ├── implicits/      # SeqImplicits
│       │       └── model/
│       │           ├── PaginatedResult.scala       # ResultHasMore / ResultNoMore ADT
│       │           ├── PartitioningResult.scala    # DB result → DTO conversion helper
│       │           └── database/                   # DB row case classes (CheckpointItemFromDB, etc.)
│       ├── test/scala/...
│       └── certs/                  # SSL certificate files (JKS)
│
├── database/                       # Database schema and migration source
│   └── src/
│       ├── main/postgres/
│       │   ├── public/             # Utility functions (global_id, jsonb helpers)
│       │   ├── runs/               # Versioned DDL/SQL for runs schema (V0.x.y.z__)
│       │   ├── flows/              # Versioned DDL/SQL for flows schema (V0.x.y.z__)
│       │   ├── validation/         # Partitioning validation functions
│       │   ├── flow_patterns/      # DDL for flow_patterns schema
│       │   └── V0.x.y.z__.ddl/sql # Top-level migrations (users, owner, hstore, etc.)
│       ├── future/                 # Draft SQL not yet in migrations (informational only)
│       └── test/scala/...         # Integration tests against live Postgres
│
├── api-tests/                      # HTTP-level API integration/smoke tests
│   ├── 1_shot/                     # Single-shot test scripts
│   └── utils/                      # Test utilities
│
├── adrs/                           # Architecture Decision Records
│   └── 01_Basics-of-FlowReader-and-PartitioningReader.drawio
│
├── project/                        # SBT meta-project (build plugins, dependencies, setup)
│   ├── Dependencies.scala          # All library versions and dependency groups
│   ├── Setup.scala                 # Scala/Java version settings, common settings
│   └── plugins.sbt                 # SBT plugins
│
├── build.sbt                       # Top-level multi-module build definition
├── publish.sbt                     # Publishing configuration
└── .scalafmt.conf                  # Scalafmt formatting rules
```

## Directory Purposes

**`model/`:**
- Purpose: Shared data contract; published as a library consumed by agent, reader, and server
- Contains: DTOs (suffix `DTO`), response envelopes, `ApiPaths` path constants, `ResultValueType` enum
- Key files: `model/src/main/scala/za/co/absa/atum/model/ApiPaths.scala`, `model/src/main/scala/za/co/absa/atum/model/dto/`, `model/src/main/scala/za/co/absa/atum/model/envelopes/`

**`agent/`:**
- Purpose: Spark application plugin library for writing measurement data
- Contains: `AtumAgent` singleton, `AtumContext` per-partitioning handle, `Dispatcher` hierarchy, measurement model
- Key files: `agent/src/main/scala/za/co/absa/atum/agent/AtumAgent.scala`, `agent/src/main/scala/za/co/absa/atum/agent/dispatcher/HttpDispatcher.scala`

**`reader/`:**
- Purpose: Client library for reading stored atum data; effect-polymorphic via `F[_]`
- Contains: `PartitioningReader`, `FlowReader`, abstract `Reader` base class
- Key files: `reader/src/main/scala/za/co/absa/atum/reader/PartitioningReader.scala`, `reader/src/main/scala/za/co/absa/atum/reader/FlowReader.scala`, `reader/src/main/scala/za/co/absa/atum/reader/core/Reader.scala`

**`server/src/main/scala/.../server/`:**
- Purpose: REST service application; vertically sliced by API version
- Contains: `Main.scala`, full `api/` package tree (v1, v2, common, database), `config/`, `model/`
- Key files: `server/src/main/scala/za/co/absa/atum/server/Main.scala`, `server/src/main/scala/za/co/absa/atum/server/api/common/http/Routes.scala`

**`database/src/main/postgres/`:**
- Purpose: All PostgreSQL DDL and stored-function SQL; managed by Flyway
- Contains: Versioned migration files per schema (`runs/`, `flows/`, `validation/`, `public/`)
- Key pattern: Files named `V{major}.{minor}.{patch}.{seq}__{description}.ddl|sql`

**`project/`:**
- Purpose: SBT build meta-project
- Key files: `project/Dependencies.scala` (all dependency versions), `project/Setup.scala` (Scala/Java version constants, merge strategies)

## Key File Locations

**Entry Points:**
- `server/src/main/scala/za/co/absa/atum/server/Main.scala`: Server application start; full ZLayer dependency graph assembly

**Configuration:**
- `server/src/main/resources/reference.conf`: Default configuration (Postgres, AWS, SSL, monitoring, pool settings)
- `server/src/main/resources/logback.xml`: Logging configuration
- `build.sbt`: Multi-module SBT project definition
- `project/Dependencies.scala`: All library version pins and dependency groups
- `project/Setup.scala`: Scala version, Java requirements, cross-build axes

**API Definitions:**
- `server/src/main/scala/za/co/absa/atum/server/api/v2/http/Endpoints.scala`: All v2 typed Tapir endpoint definitions + server endpoint wiring
- `server/src/main/scala/za/co/absa/atum/server/api/v1/http/Endpoints.scala`: v1 endpoints
- `server/src/main/scala/za/co/absa/atum/server/api/common/http/Endpoints.scala`: Health, liveness, readiness, build-info, metrics endpoints
- `model/src/main/scala/za/co/absa/atum/model/ApiPaths.scala`: All URL path string constants

**Error Hierarchy:**
- `server/src/main/scala/za/co/absa/atum/server/api/exception/AppError.scala`
- `server/src/main/scala/za/co/absa/atum/server/api/exception/DatabaseError.scala`
- `server/src/main/scala/za/co/absa/atum/server/api/exception/ServiceError.scala`

**Base Layer Traits:**
- `server/src/main/scala/za/co/absa/atum/server/api/common/controller/BaseController.scala`
- `server/src/main/scala/za/co/absa/atum/server/api/common/service/BaseService.scala`
- `server/src/main/scala/za/co/absa/atum/server/api/common/repository/BaseRepository.scala`

**DB Infrastructure:**
- `server/src/main/scala/za/co/absa/atum/server/api/database/PostgresDatabaseProvider.scala`
- `server/src/main/scala/za/co/absa/atum/server/api/database/TransactorProvider.scala`
- `server/src/main/scala/za/co/absa/atum/server/api/database/runs/Runs.scala` (DBSchema)
- `server/src/main/scala/za/co/absa/atum/server/api/database/flows/Flows.scala` (DBSchema)

## Naming Conventions

**Files:**
- Trait + implementation pairs: `{Name}.scala` (trait) + `{Name}Impl.scala` (concrete class), e.g., `PartitioningService.scala` + `PartitioningServiceImpl.scala`
- Database function wrappers: Named after the Postgres function, PascalCase, e.g., `WriteCheckpointV2.scala`, `GetPartitioningCheckpoints.scala`
- Database row model classes: Suffix `FromDB` for inbound, `ForDB` for outbound, e.g., `CheckpointItemFromDB.scala`, `PartitioningForDB.scala`
- DTO classes: Suffix `DTO`, version suffix for versioned shapes, e.g., `CheckpointV2DTO.scala`, `PartitioningSubmitV2DTO.scala`
- Config classes: Suffix `Config`, e.g., `PostgresConfig.scala`, `SslConfig.scala`
- Envelopes: Descriptive names, e.g., `SingleSuccessResponse`, `PaginatedResponse`, `ConflictErrorResponse`

**Packages (base: `za.co.absa.atum`):**
- `model.*` — shared data model
- `agent.*` — agent library
- `reader.*` — reader library
- `server.api.v1.*` / `server.api.v2.*` — versioned API layers
- `server.api.common.*` — shared server infrastructure
- `server.api.database.runs.functions.*` — runs DB function wrappers
- `server.api.database.flows.functions.*` — flows DB function wrappers

**Directories:**
- API layers follow the pattern `{version}/{layer}/` (e.g., `v2/controller/`, `v2/service/`)
- Database migration files: `V{major}.{minor}.{patch}.{seq}__{description}.{ddl|sql}` under the relevant schema subfolder

## Where to Add New Code

**New API endpoint (v2):**
1. Add DTO(s) to `model/src/main/scala/za/co/absa/atum/model/dto/`
2. Add path constant to `model/src/main/scala/za/co/absa/atum/model/ApiPaths.scala` if needed
3. Add Postgres stored function SQL to `database/src/main/postgres/runs/` or `flows/` with next version prefix
4. Create DB function wrapper class in `server/src/main/scala/za/co/absa/atum/server/api/database/runs/functions/` or `flows/functions/`; add `val layer` companion
5. Add method to `server/src/main/scala/za/co/absa/atum/server/api/v2/repository/PartitioningRepository.scala` (or relevant repo trait) and implement in `PartitioningRepositoryImpl.scala`
6. Add method to `server/src/main/scala/za/co/absa/atum/server/api/v2/service/{Domain}Service.scala` and implement in `{Domain}ServiceImpl.scala`
7. Add method to `server/src/main/scala/za/co/absa/atum/server/api/v2/controller/{Domain}Controller.scala` and implement in `{Domain}ControllerImpl.scala`
8. Define Tapir endpoint in `server/src/main/scala/za/co/absa/atum/server/api/v2/http/Endpoints.scala`; add to `serverEndpoints` list and Swagger list in `Routes.scala`
9. Wire new DB function layer in `server/src/main/scala/za/co/absa/atum/server/Main.scala`

**New DB function wrapper:**
- Location: `server/src/main/scala/za/co/absa/atum/server/api/database/runs/functions/` (runs schema) or `flows/functions/` (flows schema)
- Pattern: Extend `DoobieSingleResultFunctionWithStatus[Args, Result, Task]` or `DoobieMultipleResultFunctionWithStatus`; add `val layer: URLayer[PostgresDatabaseProvider, ThisClass]`

**New configuration key:**
- Default value: `server/src/main/resources/reference.conf`
- Config case class: `server/src/main/scala/za/co/absa/atum/server/config/`

**New shared DTO:**
- Location: `model/src/main/scala/za/co/absa/atum/model/dto/`
- Use case class; add Circe JSON codec in `model/src/main/scala/za/co/absa/atum/model/dto/package.scala` if needed

**New database migration:**
- Location: `database/src/main/postgres/runs/` or `flows/` or `validation/`
- Naming: `V{next-version}__{description}.sql` or `.ddl`
- Never modify existing migration files; always add a new versioned file

**New reader capability:**
- Location: `reader/src/main/scala/za/co/absa/atum/reader/` — add to `PartitioningReader.scala`, `FlowReader.scala`, or create new `{Domain}Reader.scala`
- Must remain effect-polymorphic; use `Reader[F]` base class and `getQuery` / `mapRequestResultF`

**Tests:**
- Unit/integration tests: Mirror the main source tree under `src/test/scala/`
- DB integration tests: `database/src/test/scala/za/co/absa/atum/database/`
- Server tests: `server/src/test/scala/za/co/absa/atum/server/`
- API smoke tests: `api-tests/`

## Special Directories

**`database/src/future/`:**
- Purpose: Draft SQL/DDL for future features not yet part of migrations
- Generated: No
- Committed: Yes (for planning purposes only; never referenced by Flyway)

**`server/certs/`:**
- Purpose: SSL certificate files (JKS format) for HTTPS support
- Generated: No
- Committed: Yes (non-production self-signed certs)

**`.sbt/matrix/`:**
- Purpose: sbt-projectmatrix cross-build output metadata
- Generated: Yes (by SBT build)
- Committed: No (in `.gitignore`)

**`target/`:**
- Purpose: SBT compiled output and assembly JARs
- Generated: Yes
- Committed: No

---

*Structure analysis: 2026-05-21*
