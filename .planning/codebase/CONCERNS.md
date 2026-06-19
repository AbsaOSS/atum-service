# Codebase Concerns

**Analysis Date:** 2026-05-21

---

## Tech Debt

**Dual API Version Maintenance (v1 + v2):**
- Issue: Full v1 and v2 API stacks are both active and maintained — duplicated controllers, services, repositories, and endpoints. No deprecation markers, no sunset timeline documented.
- Files: `server/src/main/scala/za/co/absa/atum/server/api/v1/` (full tree), `server/src/main/scala/za/co/absa/atum/server/api/v2/` (full tree)
- Impact: Every cross-cutting change (auth, error format, monitoring) must be applied to both versions. v1 service layer lacks `FlowService` which exists only in v2, indicating divergence is growing.
- Fix approach: Formally deprecate v1 endpoints, add `@deprecated` annotation or notice in `server/src/main/scala/za/co/absa/atum/server/api/v1/http/Endpoints.scala`, establish and document migration timeline.

**Unimplemented "future" SQL — Dead Database Code:**
- Issue: `database/src/future/` contains ~20 SQL files (`flow_patterns/`, `runs/`) that are not part of the Flyway migration path and have no corresponding Scala implementations.
- Files: `database/src/future/flow_patterns/*.sql`, `database/src/future/runs/*.sql`
- Impact: These represent planned but abandoned or postponed functionality (flow pattern abstractions, open/close checkpoint model). They create confusion about the intended schema and accumulate stale design.
- Fix approach: Either promote to `database/src/main/postgres/` with Flyway versioning and implement in Scala, or remove and track via GitHub issues.

**BigDecimal Serialization Workaround:**
- Issue: `Measure.scala` contains a documented workaround converting `BigDecimal` to `String` to avoid inconsistent JSON serialization across different serializers (Spark vs Circe). The root cause is not fixed.
- Files: `agent/src/main/scala/za/co/absa/atum/agent/model/Measure.scala:235-249`
- Impact: Measurement values are stored as strings in all cases, making numeric comparisons or aggregations on the stored data impossible without re-parsing.
- Fix approach: Standardize on Circe for all BigDecimal serialization; investigate if `io.circe.JsonNumber` representation resolves the discrepancy before agent sends values to server.

**TODO: Grouped Measurements — Performance Gap:**
- Issue: `AtumContext.takeMeasurements()` processes each `AtumMeasure` individually in a `Set.map`, triggering separate Spark actions per measure. Tracked as issue #98.
- Files: `agent/src/main/scala/za/co/absa/atum/agent/AtumContext.scala:64`
- Impact: For a checkpoint with N measures, N full Spark DataFrame evaluations occur. For large datasets or many measures this is a significant performance problem.
- Fix approach: Refactor `takeMeasurements` to group all measure aggregations into a single `DataFrame.agg()` call, combining into one Spark job.

**`null` Return in `build.sbt` for Conditional Module Loading:**
- Issue: `build.sbt` uses `null` returns from `lazy val` definitions to conditionally exclude `server` and `database` modules when Java < 11 is detected. The comment itself notes "if value other than null is returned, the condition doesn't seem to work."
- Files: `build.sbt:85`, `build.sbt:135`
- Impact: Fragile SBT build logic. Any SBT upgrade or plugin interaction could break the null check silently. Makes the build harder to understand.
- Fix approach: Use SBT's `projectRefs` filtering or conditional `Seq.empty` in `aggregateProjects` instead of returning `null` from a lazy val.

**sttp Version Split Across Modules:**
- Issue: Agent and reader use sttp `3.5.2` (comment: "last supported version for Java 8") while the server uses sttp `3.9.7`. This creates two incompatible version lines of the same library in the project.
- Files: `project/Dependencies.scala:34` (`sttpClient = "3.5.2"`), `project/Dependencies.scala:36` (`sttpCirceJson = "3.9.7"`)
- Impact: Bug fixes and API changes in sttp between these versions apply only to one side. Serialization behavior differences between versions could cause subtle agent↔server incompatibilities.
- Fix approach: When agent drops Java 8 support, align both on the latest sttp 3.x. Until then, document the constraint explicitly.

**Logback Version Split:**
- Issue: Agent uses `logback-classic 1.2.3` (`Versions.logback`) while server uses `1.4.7` (`Versions.logbackZio`). Version 1.2.3 has known CVEs (CVE-2021-42550).
- Files: `project/Dependencies.scala:41` (`logback = "1.2.3"`), `project/Dependencies.scala:45` (`logbackZio = "1.4.7"`)
- Impact: Agent library ships with a vulnerable transitive logging dependency.
- Fix approach: Upgrade `Versions.logback` to at least 1.4.x; verify Spark compatibility.

---

## Known Bugs

**Disabled Test Assertion on JSON Canonicalization:**
- Symptoms: The assertion `assert(row.getJsonB("partitioning").contains(partitioning))` is commented out with note "keys are reordered in JsonB and whitespaces removed."
- Files: `database/src/test/scala/za/co/absa/atum/database/runs/CreatePartitioningIfNotExistsIntegrationTests.scala:69`
- Trigger: PostgreSQL JSONB normalizes key order and removes whitespace, so the assertion fails on exact string match.
- Workaround: Test skips the assertion entirely. A proper fix would compare parsed JSON objects rather than raw strings.

**`Option.get` Calls on Database Results:**
- Symptoms: `MeasureDTO(measureName.get, measuredColumns.get)` — calling `.get` directly on `Option` values returned from database rows. If either field is `NULL` in the database, this throws `NoSuchElementException`.
- Files: `server/src/main/scala/za/co/absa/atum/server/api/v2/repository/PartitioningRepositoryImpl.scala:79`, `:99`
- Trigger: Any row where `measure_name` or `measured_columns` is NULL in the database.
- Workaround: None — results in 500 error surfaced as `GeneralDatabaseError`.

**`connectionTry.get` Throws Unchecked Exception:**
- Symptoms: `connectionTry.get` in `PostgresDataSourceWithPasswordFromSecretsManager.getConnection()` re-throws the original exception from a `Try` as an unchecked exception if all retry attempts fail.
- Files: `server/src/main/scala/za/co/absa/atum/server/api/database/PostgresDataSourceWithPasswordFromSecretsManager.scala:65`
- Trigger: Database unreachable AND Secrets Manager password refresh also fails.
- Workaround: Exception propagates up and causes connection pool errors; server does not crash but all DB calls fail.

**Unsafe `.as[]` Deserialization in Agent:**
- Symptoms: `HttpDispatcher` calls `.as[T]` (from `JsonDeserializationSyntax`) which throws on deserialization failure. Any unexpected server response format (changed API contract, error body returned where success expected) throws an unhandled exception in the Spark job.
- Files: `agent/src/main/scala/za/co/absa/atum/agent/dispatcher/HttpDispatcher.scala:58,69,89,98,107,159`
- Trigger: Server API response schema changes or server returns non-success body on unexpected status codes.
- Workaround: None — exception propagates to Spark executor.

---

## Security Considerations

**No Authentication or Authorization on Any Endpoint:**
- Risk: All HTTP endpoints (v1 and v2) use `PublicEndpoint` with zero authentication. Any client that can reach the server can read, write, or delete any partitioning, checkpoint, or additional data.
- Files: `server/src/main/scala/za/co/absa/atum/server/api/common/http/BaseEndpoints.scala` (all endpoints extend `baseEndpoint` without `securityIn`), `server/src/main/scala/za/co/absa/atum/server/api/v1/http/Endpoints.scala`, `server/src/main/scala/za/co/absa/atum/server/api/v2/http/Endpoints.scala`
- Current mitigation: Network-level access control presumed (internal cluster deployment).
- Recommendations: Add authentication (e.g., Bearer token / API key via Tapir's `securityIn`) before any external exposure. The `author` field is currently read from the **caller-supplied request body** — without authentication this can be freely spoofed, defeating auditing.

**Author Field is Caller-Supplied (No Server-Side Verification):**
- Risk: The `author` field in checkpoints, partitionings, and additional data patches is taken directly from the request payload with no server-side verification. The `AtumAgent` uses `System.getProperty("user.name")` which is trivially spoofable.
- Files: `agent/src/main/scala/za/co/absa/atum/agent/AtumAgent.scala:37` (note comment: "not supposed to be used for authorization as it can be spoofed"), all `WriteCheckpoint*`, `CreatePartitioning*`, and `CreateOrUpdateAdditionalData` database functions
- Current mitigation: None.
- Recommendations: Derive author server-side from an authenticated identity (JWT claim, Kerberos principal) once authentication is added.

**Default Credentials in `reference.conf`:**
- Risk: `reference.conf` ships with `password=changeme` (PostgreSQL) and `keyStorePassword=changeit` (SSL). If a deployment uses this file directly without overrides, real credentials are exposed.
- Files: `server/src/main/resources/reference.conf:9,20`
- Current mitigation: AWS Secrets Manager integration (`PostgresDataSourceWithPasswordFromSecretsManager`) can override the config password at runtime.
- Recommendations: Replace defaults with `""` or clearly invalid values to force explicit configuration. Document that these must always be overridden.

**Base64-Encoded JSON as GET Query Parameter:**
- Risk: The `getPartitioningEndpoint` accepts partitioning as a Base64-encoded JSON blob in a URL query parameter. This bypasses standard input validation, enables large payloads via GET requests, and complicates WAF/proxy inspection.
- Files: `server/src/main/scala/za/co/absa/atum/server/api/v2/http/Endpoints.scala:84-91`, `server/src/main/scala/za/co/absa/atum/server/api/v2/controller/PartitioningControllerImpl.scala:106`
- Current mitigation: Base64 URL-encoding is used; no server-side size limit on the query parameter value is enforced at endpoint level.
- Recommendations: Consider a POST endpoint for complex partitioning lookups, or at minimum add a size validator on the query parameter.

**PEM Certificates Committed to Repository:**
- Risk: CA certificate files are stored in `server/certs/`. If combined with a private key or used as a trust anchor, this could leak TLS configuration details.
- Files: `server/certs/Africa_Enterprise_CA1.pem`, `server/certs/Africa_Enterprise_CA2.pem`
- Current mitigation: These appear to be CA public certificates (not private keys). Confirm no private key material is present.
- Recommendations: CA public certificates are generally safe to commit. Ensure no `.key`, `.p12`, or `.jks` files with private material are ever committed.

---

## Performance Bottlenecks

**N+1 Database Queries for Checkpoint Properties:**
- Problem: When `includeProperties=true`, the server issues one additional DB query (`GetCheckpointProperties`) per checkpoint result via `ZIO.foreachPar`. For a paginated response of 1000 checkpoints (maximum `limit`), this is 1001 database round-trips.
- Files: `server/src/main/scala/za/co/absa/atum/server/api/v2/repository/CheckpointRepositoryImpl.scala:89`, `server/src/main/scala/za/co/absa/atum/server/api/v2/repository/FlowRepositoryImpl.scala:57`, `server/src/main/scala/za/co/absa/atum/server/api/common/repository/CheckpointPropertiesEnricher.scala`
- Cause: Properties are fetched individually per checkpoint rather than in a single batch query.
- Improvement path: Add a `GetCheckpointPropertiesBatch` database function that accepts a list of checkpoint IDs and returns all properties in one call. Refactor `CheckpointPropertiesEnricher` accordingly.

**Individual Spark Measure Evaluations:**
- Problem: Each measure in `AtumContext.takeMeasurements()` triggers a full Spark action on the DataFrame independently.
- Files: `agent/src/main/scala/za/co/absa/atum/agent/AtumContext.scala:63-67`
- Cause: `measures.map { m => m.function(df) }` — each `function` call independently triggers Spark evaluation.
- Improvement path: Tracked as issue #98. Combine all aggregation expressions into a single `df.agg(...)` call.

**Synchronous HTTP Dispatcher Blocking Spark Threads:**
- Problem: `HttpDispatcher` uses `OkHttpSyncBackend` — blocking I/O. Each `createCheckpoint`, `createPartitioning`, or `updateAdditionalData` call blocks a Spark thread for the full round-trip latency to the Atum server.
- Files: `agent/src/main/scala/za/co/absa/atum/agent/dispatcher/HttpDispatcher.scala:35` (`OkHttpSyncBackend`)
- Cause: Synchronous backend by design (agent targets Java 8 / Scala 2.12 without ZIO).
- Improvement path: For Java 11+ agent builds, consider an async backend. Alternatively, batch checkpoint writes (related to issue #98).

---

## Fragile Areas

**`AtumAgent` Singleton with Unbounded In-Memory Context Map:**
- Files: `agent/src/main/scala/za/co/absa/atum/agent/AtumAgent.scala:30,110-115`
- Why fragile: `object AtumAgent extends AtumAgent` is a JVM singleton. The `contexts: Map[AtumPartitions, AtumContext]` grows indefinitely — every unique partitioning combination ever used in a Spark job session is retained. Long-running Spark applications with many distinct partitionings will continuously grow heap usage.
- Safe modification: Do not add additional unbounded state to the AtumAgent singleton. If clearing contexts is needed, add an explicit `clearContexts()` method with documentation on when it is safe to call.
- Test coverage: `agent/src/test/scala/za/co/absa/atum/agent/AtumContextUnitTests.scala` tests the context logic, but no tests for memory or context eviction.

**`getExistingOrNewContext` Race Condition Potential:**
- Files: `agent/src/main/scala/za/co/absa/atum/agent/AtumAgent.scala:107-115`
- Why fragile: The synchronized block checks `contexts.getOrElse(key, { ... })`, but the `newAtumContext` by-name parameter is evaluated inside the `getOrElse` within the synchronized block. While the network call to `createPartitioning` happens **before** entering `getExistingOrNewContext`, two concurrent calls with the same partitions could both complete the network call and then the second one is silently discarded. The returned context from the second call (which was fetched from server) is thrown away and the first one returned — this could cause context state divergence if the server returned different measures/additionalData between the two calls.
- Safe modification: Add a comment documenting this behavior. The current behavior (first writer wins, silent discard of second) is safe as long as server state is authoritative.

**`processPartitioningFromDBOptionIO` Uses Opaque Error:**
- Files: `server/src/main/scala/za/co/absa/atum/server/api/v2/repository/PartitioningRepositoryImpl.scala:129,160`
- Why fragile: Both `getPartitioning` and `getPartitioningMainFlow` return `GeneralDatabaseError("Unexpected error.")` on `None` from the database. There is no context about which operation produced a `None` vs a legitimate "not found" — these cases are indistinguishable from internal errors to the caller.
- Safe modification: Replace with `NotFoundDatabaseError` or `GeneralDatabaseError(s"$operationName returned no result")` to enable proper HTTP 404 mapping.

**`CheckpointItemWithPartitioningFromDB.head` Usage:**
- Files: `server/src/main/scala/za/co/absa/atum/server/model/database/CheckpointItemWithPartitioningFromDB.scala:53,92`
- Why fragile: `checkpointItems.head.author` and `checkpointItems.head.partitioningAuthor` will throw `NoSuchElementException` if `checkpointItems` is empty. The callers in `CheckpointRepositoryImpl` ensure the list is non-empty only via the database contract, not with a Scala guard.
- Safe modification: Use `checkpointItems.headOption.map(...).getOrElse(...)` pattern or add an explicit guard.

---

## Scaling Limits

**HikariCP Pool Maximum of 10 Connections:**
- Current capacity: `maxPoolSize=10` (default in `reference.conf`)
- Limit: Under high concurrency, all 10 connections become occupied. Requests queue in HikariCP's wait queue until `connectionTimeout` (default 30s) is exceeded, at which point requests fail.
- Files: `server/src/main/resources/reference.conf:12`
- Scaling path: Increase `maxPoolSize` based on PostgreSQL server capacity and observed query latency. PgBouncer can be added as a connection proxy if the PostgreSQL server cannot handle many direct connections.

**Pagination Maximum of 1000 Records Per Page:**
- Current capacity: Paginated endpoints accept `limit` up to `1000` (`Validator.inRange(1, 1000)`).
- Limit: With `includeProperties=true`, up to 1001 sequential/parallel DB queries per request. At 1000 records this could saturate the connection pool.
- Files: `server/src/main/scala/za/co/absa/atum/server/api/v2/http/Endpoints.scala:109,124,157,190`
- Scaling path: Reduce default/max page size or fix the N+1 query pattern first.

---

## Dependencies at Risk

**`http4s-blaze-server` (Deprecated):**
- Risk: `http4s-blaze-server 0.23.15` is the last maintained version of Blaze. The http4s project has directed users to migrate to `http4s-ember-server`. No further security patches will be released for Blaze.
- Impact: Future http4s ecosystem upgrades will be blocked; security vulnerabilities in the Blaze server will go unfixed.
- Files: `project/Dependencies.scala:39` (`http4sBlazeBackend = "0.23.15"`), `server/src/main/scala/za/co/absa/atum/server/api/common/http/Server.scala:19`
- Migration plan: Replace `http4s-blaze-server` with `http4s-ember-server`. Update `Server.scala` to use `EmberServerBuilder` instead of `BlazeServerBuilder`. Minor configuration changes required for SSL and execution context wiring.

**`logback-classic 1.2.3` in Agent (CVE-2021-42550):**
- Risk: `logback 1.2.3` contains CVE-2021-42550 (JNDI injection via `JMSAppender` / `SMTPAppender`). While exploitation requires a specific configuration, shipping a library with a known CVE is a supply-chain risk.
- Impact: Any application embedding `atum-agent` receives this transitive dependency.
- Files: `project/Dependencies.scala:41`
- Migration plan: Upgrade `Versions.logback` to `1.4.x` or `1.5.x`. Verify Spark does not pin an older Logback version that conflicts.

**`sttp-client3 3.5.2` (Pinned to Java 8 Constraint):**
- Risk: sttp 3.5.2 is significantly behind 3.9.x; bug fixes, security patches, and API improvements are unavailable for the agent and reader modules.
- Impact: Agent and reader are on a different minor version than the server's test/HTTP client code.
- Files: `project/Dependencies.scala:34`
- Migration plan: Remove Java 8 support for agent/reader when the supported Spark/Scala version matrix allows it, then align to the latest sttp 3.x.

---

## Missing Critical Features

**No Authentication or Authorization:**
- Problem: The server has no auth layer at all. `PublicEndpoint` is used universally.
- Blocks: Any deployment accessible outside a fully trusted internal network is inherently insecure. Cannot enforce per-tenant or per-team data isolation.

**No API Rate Limiting:**
- Problem: There is no rate limiting, throttling, or circuit breaker at the HTTP layer. No `retry` logic in `HttpDispatcher`.
- Blocks: A single misconfigured Spark job can flood the server with requests, exhausting the connection pool and blocking other users.

---

## Test Coverage Gaps

**Key v2 Interfaces Explicitly Excluded from Coverage:**
- What's not tested: `jmf-rules.txt` explicitly excludes `PartitioningRepository`, `PartitioningController`, `PartitioningService` interfaces from JaCoCo coverage reporting. This means coverage metrics do not reflect gaps in the most central domain interfaces.
- Files: `jmf-rules.txt` (lines for `za.co.absa.atum.server.api.v2.repository.PartitioningRepository#*`, `za.co.absa.atum.server.api.v2.controller.PartitioningController#*`, `za.co.absa.atum.server.api.v2.service.PartitioningService#*`)
- Risk: Coverage report appears healthier than it is; real interface contract gaps may go undetected.
- Priority: Medium — review whether these exclusions are intentional or a misconfiguration.

**No End-to-End / Compatibility Tests for Agent ↔ Server:**
- What's not tested: The `.sbtrc` defines a `testCompatibility` alias for `*CompatibilityTests`, but no such test files exist in the codebase.
- Files: No `*CompatibilityTests.scala` found in `agent/src/test/` or `server/src/test/`
- Risk: Breaking changes in server API response format (new required fields, changed structure) are not caught before release. The agent silently throws on deserialization failure.
- Priority: High.

**`PostgresDataSourceWithPasswordFromSecretsManager` Excluded from Coverage:**
- What's not tested: The AWS Secrets Manager integration and password refresh logic has zero unit test coverage (excluded in `jmf-rules.txt`).
- Files: `server/src/main/scala/za/co/absa/atum/server/api/database/PostgresDataSourceWithPasswordFromSecretsManager.scala`, `jmf-rules.txt`
- Risk: The retry-on-SQL-exception password refresh flow and fallback to config are untested.
- Priority: Medium — this is a critical piece of production infrastructure.

**No Tests for `AtumAgent` Singleton Lifecycle:**
- What's not tested: Thread-safety of the `synchronized` context map, behavior under concurrent `getOrCreateAtumContext` calls, and memory growth of the context cache.
- Files: `agent/src/main/scala/za/co/absa/atum/agent/AtumAgent.scala`
- Risk: Concurrency bugs or memory leaks in long-running Spark applications.
- Priority: Medium.

---

*Concerns audit: 2026-05-21*
