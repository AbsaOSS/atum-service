# Coding Conventions

**Analysis Date:** 2026-05-21

## Naming Patterns

**Files:**
- All production source files use `PascalCase` matching their class/object/trait name (e.g., `PartitioningServiceImpl.scala`)
- Test files use `PascalCase` + mandatory suffix: `*UnitTests.scala`, `*IntegrationTests.scala`, or `*CompatibilityTests.scala` (enforced by CI via `AbsaOSS/filename-inspector` in `.github/workflows/test_filenames_check.yml`)
- Exceptions allowed in CI check: `TestData.scala`, `TestTransactorProvider.scala`, `ConfigProviderTest.scala`, and files under `testing/` packages
- Package objects and implicit extension files may use `lowerCamelCase` (e.g., `reader/src/main/scala/za/co/absa/atum/reader/implicits/future.scala`)

**Classes / Objects / Traits:**
- Concrete implementations: `<Name>Impl` (e.g., `PartitioningServiceImpl`, `PartitioningRepositoryImpl`, `PartitioningControllerImpl`)
- Companion objects always live in the same file as their class
- ZIO layer objects follow the singleton `object <Name>Impl { val layer: URLayer[...] = ZLayer { ... } }` pattern
- Sealed trait error hierarchies: `sealed trait XxxError extends AppError` with case class variants named `<Kind>XxxError` (e.g., `GeneralDatabaseError`, `ConflictDatabaseError`, `NotFoundDatabaseError`, `ErrorInDataDatabaseError`)

**Methods / Values:**
- `camelCase` for all methods and `val`/`var` identifiers
- Private helper vals in test objects use prefix `private val xyzMock` for Mockito mock instances
- SBT task aliases and setting keys use `camelCase` (e.g., `jacocoVersion`, `jmfRulesFile`)

**Test Suite Names:**
- Suite names in ZIO Test follow `<Feature>Suite` pattern (e.g., `"PartitioningServiceSuite"`, `"CreatePartitioningSuite"`, `"GetFlowPartitioningsSuite"`)

**Parameters:**
- Database stored procedure input parameters prefixed with `i_` (SQL convention), e.g., `i_partitioning`, `i_by_user`

## Code Style

**Formatter:** Scalafmt 3.5.3 (configured in `.scalafmt.conf`)
- Max column width: **120 characters** (also enforced by `.editorconfig` for `*.scala`)
- Indent: **2 spaces**
- Dialect: `scala212`
- `align.preset = none` — no multi-line alignment
- Unix line endings (`lineEndings = unix`)
- Scaladoc style: `AsteriskSpace`, blank first line required (`docstrings.blankFirstLine = yes`)

**Linting / Compiler flags (from `project/Setup.scala`):**
- `-unchecked`, `-deprecation`, `-feature`, `-Xfatal-warnings` — all warnings are errors
- Server/DB modules add `-language:higherKinds`, `-Ymacro-annotations`

**Format Check CI:** `.github/workflows/format_check.yml` runs `sbt scalafmt scalafmtSbt && git diff --exit-code` on every PR to master.

**EditorConfig:** `.editorconfig` enforces 2-space indentation for `*.scala`, `*.js`, `*.json`, `*.css`; 4-space for `*.xml`; 22-space for `*.sql`/`*.ddl`.

## Import Organization

**Order (Scalafmt does not enforce, but observed convention):**
1. Standard library (`java.*`, `scala.*`)
2. Third-party libraries (ZIO, Tapir, sttp, Circe, Mockito, ScalaTest)
3. Internal project packages (`za.co.absa.atum.*`)

**No wildcard imports in production code** — specific imports used throughout (e.g., `import zio._` is the single exception for ZIO's core implicits).

**Path Aliases:** None — full package paths are used everywhere.

## Error Handling

**Architecture:** Layered typed errors propagated via ZIO's typed error channel (`IO[E, A]`):

| Layer | Error Type | Location |
|-------|-----------|---------|
| Database (fa-db) | `DatabaseError` sealed trait | `server/src/main/scala/za/co/absa/atum/server/api/exception/DatabaseError.scala` |
| Service | `ServiceError` sealed trait | `server/src/main/scala/za/co/absa/atum/server/api/exception/ServiceError.scala` |
| HTTP/Controller | `ErrorResponse` (Tapir) | `model/src/main/scala/za/co/absa/atum/model/envelopes/` |
| Reader (client) | `RequestException` | `reader/src/main/scala/za/co/absa/atum/reader/exceptions/` |

**Translation chain:**
1. `BaseRepository` (`server/src/main/scala/za/co/absa/atum/server/api/common/repository/BaseRepository.scala`) catches fa-db `StatusException` subtypes and maps to `DatabaseError` variants
2. `BaseService` (`server/src/main/scala/za/co/absa/atum/server/api/common/service/BaseService.scala`) calls `repositoryCall(...)` which maps `DatabaseError` → `ServiceError` via `.mapError`
3. `BaseController` (`server/src/main/scala/za/co/absa/atum/server/api/common/controller/BaseController.scala`) calls `serviceCall(...)` which maps `ServiceError` → `ErrorResponse` (HTTP status codes)

**Pattern — always use typed error channels, never throw:**
```scala
// Repository layer
protected def dbSingleResultCallWithStatus[R](
  dbFuncCall: Task[FailedOrRow[R]],
  operationName: String
): IO[DatabaseError, R]

// Service layer
def repositoryCall[R](
  repositoryCall: IO[DatabaseError, R],
  operationName: String
): IO[ServiceError, R]

// Controller layer
def serviceCall[A, B](
  serviceCall: IO[ServiceError, A],
  onSuccessFnc: A => B = identity[A] _
): IO[ErrorResponse, B]
```

**Error logging:** `ZIO.logError(...)` and `ZIO.logDebug(...)` from `zio-logging` are used in `BaseRepository`. Production code uses `ZIO.log*` exclusively (no `println` in production — a `println(res)` in `BaseController.serviceCall` is a known issue).

**Reader module:** Uses `Either[RequestException, R]` (aliased as `RequestResult[R]`) instead of ZIO. HTTP errors and Circe decode failures are mapped to `HttpException` / `ParsingException`.

## Logging

**Framework:** ZIO Logging (`zio-logging`) in the server module. Agent uses basic `println` / `ConsoleDispatcher`.

**Patterns:**
- Use `ZIO.logError(s"...")` for errors, `ZIO.logDebug(s"...")` for success confirmations
- Log messages follow the template: `"Operation '<operationName>' [succeeded/failed]: <detail>"`
- Error messages include the operation name and original exception info

## Comments

**License Header:** All `.scala` files carry the ABSA Group Apache 2.0 license block comment at the top. This is auto-enforced by `sbt-header` (`AutomateHeaderPlugin`).

**Scaladoc:**
- Public API classes (especially in `reader` module) have Scaladoc with `@param` and `@tparam` annotations
- Format: blank first line, `AsteriskSpace` style:
```scala
/**
 *  Reader is a base class for reading data from a remote server.
 *  @param serverConfig  - the configuration how to reach the Atum server
 *  @tparam F            - the monadic effect used to get the data
 */
```
- Internal implementation classes typically have no Scaladoc

**Inline comments:** Rare. Used for TODOs linking to GitHub issues (e.g., `// TODO group measurements together: https://github.com/AbsaOSS/atum-service/issues/98`).

## Function Design

**Size:** Methods are kept small; orchestration logic is delegated to `Base*` traits (`BaseRepository`, `BaseService`, `BaseController`).

**Parameters:** Named parameters are used when constructing data objects to improve readability:
```scala
PartitioningWithIdDTO(
  id = partitioningFromDB1.id,
  partitioning = partitioningDTO1,
  author = partitioningFromDB1.author
)
```

**Return Values:**
- Server module: `IO[ErrorType, A]` (ZIO typed effects)
- Reader module: `F[RequestResult[A]]` (effect-polymorphic, `RequestResult[A] = Either[RequestException, A]`)
- Agent module: Unit-returning methods for side effects; pure functions return values directly

## Module Design

**Packages:** `za.co.absa.atum.<module>` root, with sub-packages following layer names:
- `api.v1` / `api.v2` — versioned API layers
- `api.common` — shared base traits (`BaseRepository`, `BaseService`, `BaseController`)
- `api.database.runs.functions` — individual fa-db database function wrappers
- `model.dto` — all data transfer objects
- `model.envelopes` — HTTP response envelopes

**ZIO Dependency Injection:** ZIO `ZLayer` is used for all dependency injection in the server module. Each `*Impl` object exposes a `val layer: URLayer[Dependencies, Interface]` companion member.

**Exports / Companion Objects:**
- Companion objects hold the ZIO `layer` factory and any static helpers
- Traits define the public interface; `Impl` classes hold implementation

**Versioning:** API is versioned at the package level (`api.v1`, `api.v2`). V1 and V2 share common base traits but maintain separate controller/service/repository classes.

---

*Convention analysis: 2026-05-21*
