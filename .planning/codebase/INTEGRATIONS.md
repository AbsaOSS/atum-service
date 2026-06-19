# External Integrations

**Analysis Date:** 2026-05-21

## APIs & External Services

**AWS Secrets Manager:**
- Service: AWS Secrets Manager (SDK v2 `software.amazon.awssdk:secretsmanager:2.31.48`)
- Purpose: Fetches the PostgreSQL `atum_user` database password at runtime; supports automatic secret rotation
- Client: `AWSSDKs.secretsManagerSyncClient` — synchronous `SecretsManagerClient` built with `DefaultCredentialsProvider`; initialized in `server/src/main/scala/za/co/absa/atum/server/api/database/AWSSDKs.scala`
- Auth: AWS `DefaultCredentialsProvider` chain (environment variables, instance profile, etc.)
- Integration class: `server/src/main/scala/za/co/absa/atum/server/api/database/PostgresDataSourceWithPasswordFromSecretsManager.scala`
- Config key: `postgres.passwordSecretId` in `server/src/main/resources/reference.conf` (default value: `"serviceUserSecretKey"`)
- Region: `aws.region` in `reference.conf` (default: `"af-south-1"`)
- Fallback: If Secrets Manager is unreachable, password falls back to `postgres.password` from config

**AWS STS:**
- Service: AWS Security Token Service (SDK v2 `software.amazon.awssdk:sts:2.31.48`)
- Purpose: Supports assumed-role credential scenarios for the `DefaultCredentialsProvider` chain
- Client: Not directly instantiated — consumed transitively by AWS credential resolution
- Auth: Same `DefaultCredentialsProvider` as Secrets Manager

**Atum Server REST API (agent → server):**
- Purpose: The `agent` module dispatches measurement data (checkpoints, partitionings, additional data) to the `atum-server` via HTTP REST
- Client: sttp-client3 3.5.2 + OkHttp backend (`agent/src/main/scala/za/co/absa/atum/agent/dispatcher/HttpDispatcher.scala`)
- Auth: None detected (plain HTTP calls to configured server URL)
- Config: Typesafe Config (`typesafe-config 1.4.2`) — agent reads server host/port from config

**Atum Server REST API (reader → server):**
- Purpose: The `reader` module reads measurement data from the server
- Client: sttp-client3 3.5.2 with optional Cats IO or Future backend (`reader/src/main/scala/za/co/absa/atum/reader/core/Reader.scala`)
- Backends: `sttp-cats` (optional), standard Future (via `reader/src/main/scala/za/co/absa/atum/reader/implicits/future.scala` and `io.scala`)
- Auth: None detected

## Data Storage

**Databases:**
- Type: PostgreSQL 15
- Connection: JDBC via `PGSimpleDataSource` extended by `PostgresDataSourceWithPasswordFromSecretsManager`
- Pool: HikariCP (via fa-db/doobie) — min idle 4, max pool 10, max lifetime 600s
- Config keys in `server/src/main/resources/reference.conf`:
  - `postgres.serverName` (default: `localhost`)
  - `postgres.portNumber` (default: `5432`)
  - `postgres.databaseName` (default: `atum_db`)
  - `postgres.user` (default: `atum_user`)
  - `postgres.password` (default: `changeme` — overridden by Secrets Manager in production)
  - `postgres.passwordSecretId` (default: `"serviceUserSecretKey"`)
- JDBC URL for Flyway: `jdbc:postgresql://localhost:5432/atum_db` (defined in `project/FlywayConfiguration.scala`)
- Database access layer: fa-db doobie (`za.co.absa.db.fa-db:doobie:0.7.0`) — wraps stored procedure calls as Scala types
- Transactor provider: `server/src/main/scala/za/co/absa/atum/server/api/database/TransactorProvider.scala`
- DB provider: `server/src/main/scala/za/co/absa/atum/server/api/database/PostgresDatabaseProvider.scala`

**Schema Migrations:**
- Tool: Flyway (via `flyway-sbt 7.4.0` plugin)
- Migration scripts: `database/src/main/postgres/` — SQL and DDL files following Flyway versioned naming (`V{semver}__{description}.sql/.ddl`)
- Baseline version: `0.1.0.42`
- Locations config: `filesystem:database/src/main/postgres`
- Schema covers: `runs` schema (partitionings, checkpoints, measures, additional data, flows), `public` schema (utility functions), user/role DDL
- Latest notable migrations: `V0.6.2.0__atum_reader.ddl` (reader role), `V0.5.1.5__get_checkpoint_properties.sql`

**File Storage:**
- Not detected — no S3, GCS, or local file storage integration beyond JAR artifacts uploaded to GitHub Releases

**Caching:**
- Not detected — no Redis, Memcached, or in-memory caching layer; HikariCP connection pool is the only pooling mechanism

## Authentication & Identity

**Auth Provider:**
- Custom — no third-party identity provider (e.g., OAuth, OIDC) detected
- Database: `atum_user` for runtime; `atum_owner` for integration tests (see `server/src/test/resources/reference.conf`)
- SSL/TLS (optional): JKS keystore support for HTTPS on port 8443; configured via `ssl.enabled`, `ssl.keyStorePath`, `ssl.keyStorePassword` in `reference.conf`; implementation in `server/src/main/scala/za/co/absa/atum/server/api/common/http/Server.scala`

## Monitoring & Observability

**Metrics — Prometheus:**
- Metrics library: `zio-metrics-connectors-prometheus 2.3.1` + `http4s-prometheus-metrics 0.23.6`
- Exposed endpoints (HTTP port 8080):
  - `/metrics` — HTTP layer metrics (http4s)
  - `/hikari-metrics` — HikariCP connection pool metrics
  - `/zio-metrics` — ZIO runtime metrics (JVM stats, ZIO fiber stats; scrape interval configurable via `monitoring.jvm.intervalInSeconds`)
- Config toggles: `monitoring.http.enabled`, `monitoring.hikari.enabled`, `monitoring.jvm.enabled` in `reference.conf`
- Prometheus scrape config: `server/prometheus.yml`

**Metrics — Grafana:**
- Grafana (`grafana/grafana:latest`) is provided as a local dev Docker Compose service only
- Docker Compose: `server/docker-compose.yml` — starts Prometheus (port 9090) and Grafana (port 3000)
- Not integrated as a production deployment artifact

**Error Tracking:**
- None — no Sentry, Rollbar, or equivalent detected

**Logs:**
- Logback (SLF4J) via `logback-classic`; ZIO-logging with SLF4J bridge (`zio-logging-slf4j2`)
- Log output: standard SLF4J configuration; no centralized log aggregation service detected

## CI/CD & Deployment

**Hosting:**
- Server: JVM process running the fat JAR; no containerized deployment config detected beyond local Docker Compose for monitoring
- Client libraries: Maven Central via Sonatype

**CI Pipeline:**
- GitHub Actions (`.github/workflows/`)
  - `build.yml` — triggered on PRs; two jobs:
    1. `test-agent-reader-and-model` — Java 8, runs `sbt testAllStandard` + `sbt doc`
    2. `test-database-and-server` — Java 11, spins up PostgreSQL 15 service, runs unit tests, `sbt flywayMigrate`, integration tests, doc
  - `format_check.yml` — Scalafmt format check
  - `license_check.yml` — Apache-2.0 header verification (via sbt-header)
  - `jacoco_report.yml` — code coverage reporting
  - `release_publish.yml` — triggered on GitHub release tags; publishes libraries to Sonatype (`sbt ci-release`) and uploads server fat JAR to GitHub Release assets
  - `release_draft.yml` — release draft automation
  - `assign_issue_to_project.yml` — assigns new issues to GitHub Project board (org project #7)
  - `test_filenames_check.yml` — enforces test file naming conventions

**Dependency Management:**
- `dependabot.yml` — Dependabot enabled for automated dependency updates

## Environment Configuration

**Required env vars / config at runtime:**
- `postgres.serverName` / `postgres.portNumber` / `postgres.databaseName` — DB coordinates
- `postgres.user` / `postgres.password` — DB credentials (password overridden by Secrets Manager when available)
- `postgres.passwordSecretId` — AWS Secrets Manager secret ID for DB password
- `aws.region` — AWS region for Secrets Manager client (default: `af-south-1`)
- `ssl.enabled` / `ssl.keyStorePath` / `ssl.keyStorePassword` — TLS termination (optional)
- AWS credentials: resolved via `DefaultCredentialsProvider` chain (env vars, instance profile, `~/.aws/credentials`, etc.)

**Secrets location:**
- Production DB password stored in AWS Secrets Manager (secret ID configured via `postgres.passwordSecretId`)
- Sonatype and PGP secrets stored as GitHub Actions repository secrets (`PGP_PASSPHRASE`, `PGP_SECRET`, `SONATYPE_PASSWORD`, `SONATYPE_USERNAME`)
- GitHub PAT stored as secret `PAT_REPO_PROJECT_DISCUSS` for project board automation

## API Documentation

**Swagger UI:**
- Tapir auto-generates OpenAPI docs; served via `tapir-swagger-ui-bundle`
- Integrated into the running server at a Swagger UI route (configured via `tapir-swagger-ui-bundle` in server dependencies)

## Webhooks & Callbacks

**Incoming:**
- None detected — no inbound webhook endpoints registered

**Outgoing:**
- None detected — no outbound webhook dispatch; agent-to-server communication is synchronous HTTP REST (not event-driven webhooks)

---

*Integration audit: 2026-05-21*
