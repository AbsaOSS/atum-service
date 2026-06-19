# Testing Patterns

**Analysis Date:** 2026-05-21

## Test Framework

**Runner:** SBT test runner with multiple test frameworks used per module:

| Module | Framework(s) | Test Type |
|--------|-------------|-----------|
| `server` | ZIO Test (`zio.test.sbt.ZTestFramework`) + ScalaTest | Unit tests (ZIO), model tests (ScalaTest) |
| `agent` | ScalaTest (`AnyFlatSpec` / `AnyFlatSpecLike`) | Unit tests |
| `model` | ScalaTest (`AnyFunSuiteLike`, `AnyFlatSpecLike`) | Unit tests |
| `reader` | ScalaTest (`AnyFunSuiteLike`) | Unit tests |
| `database` | Balta (`za.co.absa.balta.DBTestSuite`) | Integration tests (require live PostgreSQL) |

**ZIO Test Config (server module):**
```scala
testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
```
Defined in `build.sbt` under the `server` module settings.

**Balta (database integration testing):** Version `0.1.0` (see `project/Dependencies.scala`). An ABSA in-house framework for calling PostgreSQL stored procedures in test transactions and inspecting resulting table state.

**ScalaTest Version:** 3.2.15 with Mockito integration via `scalaMockito` 1.17.12.

**Assertion Libraries:**
- ZIO Test: `assertTrue(...)`, `assertZIO(...)(assertion)`, `failsWithA[ErrorType]`
- ScalaTest: `assert(...)`, `shouldBe`, Matchers trait
- Balta: `assert(row.getInt(...).contains(...))`, `assert(queryResult.hasNext)`

**Run Commands:**
```bash
# Run all standard (unit) tests for agent, reader, model
sbt testAllStandard

# Run unit tests for a specific module
sbt "project server" test
sbt "project agent" test

# Run integration tests (requires PostgreSQL running on localhost:5432)
sbt "project database" testIT
sbt "project server" testIT

# Prepare database for integration tests
sbt flywayMigrate

# Run coverage (JaCoCo per module)
sbt "project agent" jacoco
sbt "project reader" jacoco
sbt "project model" jacoco
sbt "project server" jacoco
```

**Parallelism:** Disabled globally for all modules:
```scala
Test / parallelExecution := false
```
(in `project/Setup.scala` `commonSettings`)

---

## Test File Organization

**Location:** Tests are co-located under `src/test/scala/` within each module, mirroring the `src/main/scala/` package structure exactly.

**Example structure:**
```
server/
  src/main/scala/za/co/absa/atum/server/api/v2/service/PartitioningServiceImpl.scala
  src/test/scala/za/co/absa/atum/server/api/v2/service/PartitioningServiceUnitTests.scala

database/
  src/main/postgres/runs/R__create_partitioning.sql
  src/test/scala/za/co/absa/atum/database/runs/CreatePartitioningIntegrationTests.scala
```

**Naming (CI-enforced):**
- Unit tests: `*UnitTests.scala`
- Integration tests: `*IntegrationTests.scala`
- Compatibility tests: `*CompatibilityTests.scala`

CI fails if any file under `**/src/test/scala/**` does not match one of these suffixes (`.github/workflows/test_filenames_check.yml`).

**Exceptions to naming rule:** `TestData.scala`, `TestTransactorProvider.scala`, `ConfigProviderTest.scala`, and files under `testing/` packages are explicitly excluded.

---

## Test Structure

### ZIO Test (server module unit tests)

```scala
object PartitioningServiceUnitTests extends ZIOSpecDefault with TestData {

  // Mockito mock setup at object level (shared across all tests)
  private val partitioningRepositoryMock = mock(classOf[PartitioningRepository])
  when(partitioningRepositoryMock.createPartitioning(partitioningSubmitV2DTO1))
    .thenReturn(ZIO.succeed(PartitioningWithIdDTO(1L, Seq.empty, "author")))
  when(partitioningRepositoryMock.createPartitioning(partitioningSubmitV2DTO2))
    .thenReturn(ZIO.fail(ConflictDatabaseError("Partitioning already exists")))

  private val mockLayer = ZLayer.succeed(partitioningRepositoryMock)

  override def spec: Spec[TestEnvironment with Scope, Any] = {
    suite("PartitioningServiceSuite")(
      suite("CreatePartitioningSuite")(
        test("Returns expected Right with PartitioningWithIdDTO") {
          for {
            result <- PartitioningService.createPartitioning(partitioningSubmitV2DTO1)
          } yield assertTrue(result == PartitioningWithIdDTO(1L, Seq.empty, "author"))
        },
        test("Returns expected ConflictServiceError") {
          for {
            result <- PartitioningService.createPartitioning(partitioningSubmitV2DTO2).exit
          } yield assertTrue(
            result == Exit.fail(ConflictServiceError("..."))
          )
        },
        test("Returns expected GeneralServiceError") {
          assertZIO(PartitioningService.createPartitioning(partitioningSubmitV2DTO3).exit)(
            failsWithA[GeneralServiceError]
          )
        }
      )
    )
  }.provide(
    ServiceImpl.layer,      // the implementation under test
    mockLayer               // the mocked dependency
  )
}
```

**Key patterns:**
- Test object extends `ZIOSpecDefault with TestData`
- Specs use nested `suite(...)(...)` for grouping by operation
- ZIO layer provision via `.provide(ImplUnderTest.layer, mockDependencyLayer)` at the end of the spec
- Success assertions: `assertTrue(result == expected)`
- Failure assertions via `.exit`: `assertTrue(result == Exit.fail(SpecificError("msg")))` or `assertZIO(...)(failsWithA[ErrorType])`

### ScalaTest (agent, model, reader modules)

```scala
class AtumContextUnitTests extends AnyFlatSpec with Matchers {
  "methodName" should "description" in {
    // Mockito setup inline
    val mockAgent = mock(classOf[AtumAgent])
    when(mockAgent.currentUser).thenReturn("authorTest")

    // Exercise
    val result = subject.someMethod(input)

    // Assert
    assert(result == expected)
    result.name shouldBe "expected"  // Matchers style also used
  }
}
```

**Styles observed:**
- `AnyFlatSpec with Matchers` — agent module, uses `"subject" should "behavior" in { ... }` style
- `AnyFunSuiteLike` — model and reader modules, uses `test("description") { ... }` style
- Both styles use `assert(...)` and ScalaTest `should` matchers interchangeably

### Balta Database Integration Tests

```scala
class CreatePartitioningIntegrationTests extends DBTestSuite {

  private val fncCreatePartitioning = "runs.create_partitioning"
  private val partitioning = JsonBString("""{ ... json ... }""".stripMargin)

  test("Partitioning created") {
    val partitioningID = function(fncCreatePartitioning)
      .setParam("i_partitioning", partitioning)
      .setParam("i_by_user", "Fantômas")
      .setParamNull("i_parent_partitioning_id")
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("Partitioning created"))
        row.getLong("id_partitioning").get
      }

    // Verify side effects in tables
    table("runs.partitionings").where(add("id_partitioning", partitioningID)) { result =>
      val row = result.next()
      assert(row.getString("created_by").contains("Fantômas"))
      assert(row.getOffsetDateTime("created_at").contains(now()))
    }
  }
}
```

**Key patterns:**
- Extend `za.co.absa.balta.DBTestSuite` — each test runs in a transaction that is rolled back after the test
- Call DB functions via `function("schema.function_name").setParam(...).execute { queryResult => ... }`
- Null params via `.setParamNull("param_name")`
- Assert return rows: `row.getInt("status").contains(expectedCode)`
- Verify table state via `table("schema.table").where(add("column", value)) { cursor => ... }`
- `now()` helper from `DBTestSuite` used for timestamp assertions

---

## Mocking

**Framework:** Mockito for Java via `scalaMockito` 1.17.12

**Patterns:**

```scala
// Create mock
private val repositoryMock = mock(classOf[PartitioningRepository])

// Stub ZIO-returning methods
when(repositoryMock.createPartitioning(specificInput))
  .thenReturn(ZIO.succeed(result))

when(repositoryMock.createPartitioning(otherInput))
  .thenReturn(ZIO.fail(ConflictDatabaseError("message")))

// Wrap in ZIO Layer for provision
private val mockLayer = ZLayer.succeed(repositoryMock)

// Capture arguments
val argument = ArgumentCaptor.forClass(classOf[CheckpointDTO])
verify(mockAgent).saveCheckpoint(argument.capture())
val captured: CheckpointDTO = argument.getValue

// Verify call count
verify(mockAgent, times(2)).saveCheckpoint(argumentCaptor.capture())
```

**What to Mock:**
- Repository interfaces in service unit tests
- Controller interfaces in HTTP endpoint unit tests
- `AtumAgent` in agent/context unit tests

**What NOT to Mock:**
- `TestData` shared fixture objects — use the trait directly
- ZIO effects themselves — stub the interface method to return `ZIO.succeed/fail`

**Stub location:** Mockito stubs are set up **at the object/class level** (outside test methods) when using `ZIOSpecDefault`, so they apply to all tests in the suite. In ScalaTest tests, mocks may be created per-test inside the `in { }` block.

---

## Fixtures and Factories

**Shared Test Data:** `server/src/test/scala/za/co/absa/atum/server/api/TestData.scala`

All server unit tests mix in `trait TestData` to get pre-built DTO instances:
```scala
object MyUnitTests extends ZIOSpecDefault with TestData {
  // Use: partitioningDTO1, checkpointDTO1, measureDTO1, uuid1, etc.
}
```

**Available fixture groups in `TestData`:**
- `partitioningDTO1/2/3` — `PartitioningDTO` (Seq of PartitionDTO)
- `partitioningSubmitV2DTO1/2/3` — `PartitioningSubmitV2DTO`
- `partitioningWithIdDTO1/2` — `PartitioningWithIdDTO`
- `checkpointDTO1/2/3/4` and `checkpointV2DTO1/2/3/4`
- `measureDTO1/2`, `measureResultDTO1/2`, `measurementsDTO1/2`
- `additionalDataDTO1/2/3`, `additionalDataPatchDTO1`
- `flowDTO1/2`
- `uuid1`, `uuid2` — random UUIDs generated at object init
- DB-layer fixtures: `partitioningFromDB1/2`, `checkpointItemNotPaginatedFromDB`, `checkpointItemPaginatedFromDB`, etc.

**No equivalent `TestData` trait in agent/model/reader** — those modules build fixtures inline in each test.

**JSON fixtures in database tests:** Inline `JsonBString(""" ... """.stripMargin)` literals used directly in each integration test file.

---

## Coverage

**Tool:** JaCoCo (custom `FilteredJacocoAgentPlugin` in `project/FilteredJacocoAgentPlugin.scala`)

**Per-module thresholds (from `.github/workflows/jacoco_report.yml`):**

| Module | Overall Coverage | Changed Files Coverage |
|--------|-----------------|----------------------|
| `atum-reader` | 73% | 80% |
| `atum-agent` | 67% | 80% |
| `atum-model` | 35% | 80% |
| `atum-server` | 71% | 80% |

**Global targets:**
- `coverage-overall: 80.0`
- `coverage-changed-files: 80.0`
- `coverage-per-changed-file: 0.0` (not enforced per file)
- `check-overall-coverages: true`

**Reports:** JaCoCo XML reports generated at `**/target/**/jacoco/report/jacoco.xml` and posted as PR comments via `MoranaApps/jacoco-report` GitHub Action.

**Run locally:**
```bash
sbt "project agent" jacoco
sbt "project reader" jacoco
sbt "project model" jacoco
sbt "project server" jacoco
# Report generated at: <module>/target/<scala-version>/jacoco/report/jacoco.xml
```

---

## Test Types

### Unit Tests (`*UnitTests.scala`)

**Server (ZIO Test):**
- Scope: Single layer (controller, service, or repository) with all dependencies mocked
- Location: `server/src/test/scala/za/co/absa/atum/server/api/v1/` and `.../v2/` mirroring main structure
- Sub-types: controller tests, service tests, repository tests, HTTP endpoint tests (using `TapirStubInterpreter`)

**Agent (ScalaTest):**
- Location: `agent/src/test/scala/za/co/absa/atum/agent/`
- Tests AtumContext, AtumAgent, dispatchers, and measure builders
- Agent tests involving Spark use `SparkSession.builder().master("local")` — no separate Spark test fixture

**Model (ScalaTest):**
- Location: `model/src/test/scala/za/co/absa/atum/model/`
- Tests: DTO serialization/deserialization (Circe), `ErrorResponse` HTTP status mapping, `AtumPartitions` type

**Reader (ScalaTest):**
- Location: `reader/src/test/scala/za/co/absa/atum/reader/`
- Tests HTTP client logic using `SttpBackendStub.synchronous` / `SttpBackendStub[Future, ...]` / `SttpBackendStub[IO, ...]`
- Separate test classes for each effect backend: `Reader_FutureUnitTests`, `Reader_CatsIOUnitTests`

### Integration Tests (`*IntegrationTests.scala`)

**Database module (Balta):**
- Location: `database/src/test/scala/za/co/absa/atum/database/`
- Tests each PostgreSQL stored procedure directly via `function("schema.func").setParam(...).execute { ... }`
- Each test runs in an isolated transaction (rolled back post-test by Balta)
- Require PostgreSQL 15 running on `localhost:5432` with database `atum_db` (created by `sbt flywayMigrate`)

**Server module (ZIO Test):**
- Location: `server/src/test/scala/za/co/absa/atum/server/api/database/runs/functions/`
- Tests server-side fa-db function wrappers against real PostgreSQL
- Mirrors database module tests but exercised through the server's `fa-db` function objects

**CI separation:** Database/server integration tests run in a separate CI job (`test-database-and-server`) using a `postgres:15` service container. Unit tests for agent/reader/model run in `test-agent-reader-and-model` with Java 8.

### HTTP Endpoint Tests (server, ZIO Test)

These are classified as unit tests but exercise the full Tapir endpoint logic:
```scala
// Uses TapirStubInterpreter + SttpBackendStub to simulate HTTP
val backendStub = TapirStubInterpreter(
  SttpBackendStub.apply(new RIOMonadError[PartitioningController])
)
  .whenServerEndpoint(serverEndpoint)
  .thenRunLogic()
  .backend()

val response = basicRequest
  .post(uri"https://test.com/api/v2/partitionings")
  .body(dto)
  .send(backendStub)

assertZIO(response.map(_.code))(equalTo(StatusCode.Created))
```

Location: `server/src/test/scala/za/co/absa/atum/server/api/v2/http/`

---

## Common Patterns

### Async Testing (ZIO)

```scala
// For-comprehension style — most common
test("success case") {
  for {
    result <- ServiceUnderTest.operation(input)
  } yield assertTrue(result == expected)
}

// Exit-based for failure assertions (exact error match)
test("specific error") {
  for {
    result <- ServiceUnderTest.operation(badInput).exit
  } yield assertTrue(result == Exit.fail(SpecificError("message")))
}

// assertZIO + failsWithA (type-only failure match)
test("general error type") {
  assertZIO(ServiceUnderTest.operation(badInput).exit)(
    failsWithA[GeneralServiceError]
  )
}
```

### Async Testing (ScalaTest + Future)

```scala
// Reader tests use direct execution with Identity monad or Await
test("should return mapped result") {
  val result = reader.operation(input)
  assert(result == Right(expected))
}
```

### Error Testing (ScalaTest)

```scala
// Assert thrown exceptions
intercept[SomeException] {
  subject.methodThatThrows()
}

// Assert error values  
assert(result == Left(expectedError))
```

---

*Testing analysis: 2026-05-21*
