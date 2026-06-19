# Technology Stack

**Analysis Date:** 2026-05-21

## Languages

**Primary:**
- Scala 2.13.13 — server, database modules (all server-side code)
- Scala 2.12.18 — cross-compiled for agent, reader, model client libraries

**Secondary:**
- Java 11 — server and database module JVM target (javac `-source 11 -target 11`)
- Java 8 (1.8) — client modules JVM target (agent, reader, model cross-compiled to Java 8 bytecode)
- SQL / PL/pgSQL — PostgreSQL stored procedures and migration scripts in `database/src/main/postgres/`
- Python — utility scripts in `api-tests/utils/` (collection extractor, CSV report analysis)

## Runtime

**Environment:**
- JVM — required ≥ Java 1.8; recommended Java 11+ for server and database modules
- Java 11 required to load `server` and `database` modules (checked at build init in `build.sbt`)

**Package Manager:**
- sbt 1.11.5
- Lockfile: not used (sbt does not use a lockfile by default)
- Dependency cache: Coursier (used in GitHub Actions via `coursier/cache-action`)

## Frameworks

**Core (server):**
- ZIO 2.0.19 — primary effect system and runtime for the server; all business logic runs as ZIO effects
- http4s-blaze-server 0.23.15 — HTTP server backend (`server/src/main/scala/.../api/common/http/Server.scala`)
- Tapir 1.9.6 — type-safe HTTP API endpoint definitions + Swagger UI bundle; declared in `server/src/main/scala/.../api/v1/http/Endpoints.scala`, `api/v2/http/Endpoints.scala`, `api/common/http/Endpoints.scala`
- fa-db (doobie) 0.7.0 — functional database access layer over PostgreSQL (org: `za.co.absa.db.fa-db`)

**Client libraries:**
- Apache Spark 3.5.5 — provided dependency for the `agent` module; enables measurement on Spark jobs
- spark-commons 0.6.3 — ABSA internal Spark utilities used by agent
- Cats Effect 3.4.11 — optional effect backend for `reader` module (alongside ZIO and Future backends)

**JSON Serialization:**
- Circe 0.14.7 (`circe-core`, `circe-parser`, `circe-generic`) — used across model, server, reader

**HTTP Client:**
- sttp-client3 3.5.2 — agent and reader modules (last version supporting Java 8)
- sttp-client3 3.9.7 — server module tests and STTP-Circe integration
- OkHttp backend (sttp) — used by agent for synchronous HTTP dispatch
- async-http-client-backend-zio (sttp) — used by server for async ZIO-based HTTP client

**Configuration:**
- ZIO-config 4.0.1 (+ magnolia, typesafe extensions) — server configuration loading
- Typesafe Config 1.4.2 — used by agent and model modules

**Metrics/Observability:**
- ZIO-metrics-connectors-prometheus 2.3.1 — ZIO runtime and business metrics exposed to Prometheus
- http4s-prometheus-metrics 0.23.6 — HTTP-level metrics
- HikariCP — connection pool metrics (via fa-db/doobie pool); exposed at `/hikari-metrics`
- Logback Classic 1.4.7 (server), 1.2.3 (agent) — SLF4J logging implementation
- ZIO-logging 2.2.0 + SLF4J bridge — structured ZIO logging

**Testing:**
- ScalaTest 3.2.15 — primary unit/integration test framework (all modules)
- Mockito-scala 1.17.12 — mocking in unit tests
- ZIO Test 2.0.19 — ZIO-native test framework for server module; configured via `ZTestFramework`
- Specs2 4.10.0 — used in model module tests
- Balta 0.1.0 — ABSA integration testing library for database module and agent
- JUnit Interface 0.13.3 — JUnit bridge for sbt test runner
- Tapir stub server — server API endpoint stubbing in tests
- Postman / Newman ≥ v6 — API-level end-to-end tests (`api-tests/`)

**Build/Dev:**
- sbt-assembly 2.2.0 — fat JAR packaging for server deployment
- sbt-projectmatrix 0.10.0 — cross-version/cross-axis matrix builds
- flyway-sbt 7.4.0 — database migration management
- sbt-scalafmt 2.5.0 — Scalafmt code formatting (config: `.scalafmt.conf`)
- sbt-header (de.heikoseeberger) 5.7.0 — Apache-2.0 license header enforcement
- sbt-git 2.1.0 — git-based versioning
- sbt-buildinfo 0.13.1 — generates `BuildInfo` object with version metadata; package `za.co.absa.atum.server.api.common.http`
- sbt-ci-release 1.11.2 — Sonatype publish automation
- FilteredJacocoAgentPlugin — custom Jacoco code coverage plugin (`project/FilteredJacocoAgentPlugin.scala`)

## Key Dependencies

**Critical:**
- `dev.zio:zio:2.0.19` — all server concurrency, error handling, and resource management
- `com.softwaremill.sttp.tapir:tapir-http4s-server-zio:1.9.6` — ZIO + http4s + Tapir integration glue
- `za.co.absa.db.fa-db:doobie:0.7.0` — PostgreSQL function-call abstraction layer; all DB calls go through this
- `org.apache.spark:spark-core/spark-sql:3.5.5` — provided runtime for agent; version axis drives cross-build
- `software.amazon.awssdk:secretsmanager:2.31.48` — AWS Secrets Manager for DB password retrieval

**Infrastructure:**
- `org.postgresql:postgresql:42.6.0` — JDBC driver; used by Flyway migrations and fa-db
- `ch.qos.logback:logback-classic:1.4.7` — runtime logging
- `io.circe:circe-*:0.14.7` — JSON codec for API DTOs across all modules
- `za.co.absa:balta:0.1.0` — ABSA integration test DB helpers

## Configuration

**Environment:**
- Typesafe HOCON (`reference.conf`) — primary config format
- Server config file: `server/src/main/resources/reference.conf`
- Test config file: `server/src/test/resources/reference.conf`
- Key config sections: `postgres`, `aws`, `ssl`, `monitoring`
- `passwordSecretId` in postgres config points to AWS Secrets Manager secret ID

**Build:**
- `build.sbt` — root multi-module build
- `project/Dependencies.scala` — all dependency version constants and dependency groups
- `project/Setup.scala` — Scala/Java version matrix and compiler options
- `project/FlywayConfiguration.scala` — Flyway DB connection settings (defaults to `localhost:5432/atum_db`)
- `project/plugins.sbt` — sbt plugin declarations
- `.scalafmt.conf` — Scalafmt 3.5.3, `runner.dialect = scala212`, max column 120

## Platform Requirements

**Development:**
- Java 11+ recommended (Java 8 minimum for client modules only)
- sbt 1.11.5
- PostgreSQL 15 (see CI service definition in `.github/workflows/build.yml`)
- Node.js ≥ v16 + Newman ≥ v6 for API test runs

**Production:**
- JVM (Java 11) server deployment as fat JAR (`atum-server-{version}.jar`) built via `sbt assembly`
- Server JAR uploaded to GitHub Releases on each release tag
- Client libraries (`atum-agent`, `atum-model`, `atum-reader`) published to Sonatype/Maven Central
- PostgreSQL database with Flyway-managed schema
- AWS credentials (DefaultCredentialsProvider chain) required at runtime for Secrets Manager access

---

*Stack analysis: 2026-05-21*
