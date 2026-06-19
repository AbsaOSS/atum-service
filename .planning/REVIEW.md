# Code Review — Issue #205 DB Connection Bottleneck (All Phases)

**Reviewed:** 2026-05-22  
**Depth:** deep (cross-file call-chain analysis)  
**Files Reviewed:** 24 across Phases 1–3  
**Status:** all_findings_resolved

---

## Summary

5 Critical findings identified; **all 5 resolved** before commit.

> All fixes applied in the same change set as the phase implementations. No open findings remain.

| Phase | File | Finding |
|-------|------|---------|
| 3 | `RetryConfig.scala` / `HttpDispatcher.scala` | CRITICAL — negative `maxRetries` causes `IllegalStateException` on every request |
| 3 | `HttpDispatcher.scala` | CRITICAL — multiplication overflow defeats the `safeAttempt` guard in `computeDelay` |
| 3 | `HttpDispatcher.scala` | CRITICAL — `null` sentinel assigned to a non-nullable-typed `val`; NPE on refactor |
| 3 | `HttpDispatcher.scala` | CRITICAL — post-loop `throw` branches are provably dead code |
| 1 | `PostgresConfig.scala` / `TransactorProvider.scala` | CRITICAL — `connectionTimeout` has no lower-bound guard; HikariCP minimum is 250 ms |

Phase 2 (semaphore) is clean: `TaskLayer` is the correct type, permit arithmetic is correct,
`withPermit` guarantees release on interrupt/defect via `ZIO.guarantee`, and all 17
`repositoryCall` sites across the 5 service impls are wrapped consistently.

---

## Findings

---

### [CRITICAL] Negative `maxRetries` silently disables all HTTP dispatch

**File:** `agent/src/main/scala/za/co/absa/atum/agent/dispatcher/RetryConfig.scala:60–65`  
**Also affects:** `agent/src/main/scala/za/co/absa/atum/agent/dispatcher/HttpDispatcher.scala:219`

**Issue:**  
`RetryConfig.fromConfig` performs no input validation. If an operator configures
`atum.dispatcher.http.retry.max-retries = -1`, the value is accepted and stored.
In `withRetry`, the loop guard is:

```scala
while (attempt <= retryConfig.maxRetries)   // attempt starts at 0
```

With `maxRetries = -1`, the condition `0 <= -1` is immediately false. The loop body never
executes — **no HTTP call is ever made** — and execution falls through to:

```scala
throw new IllegalStateException("withRetry exited without returning a response")
```

Every single HTTP operation from the agent throws an uncaught `IllegalStateException`.
The failure is silent at configuration time and explosive at runtime.

**Impact:** Complete agent data loss for any deployment that sets a negative retry value.
The `IllegalStateException` is not a subtype of the documented `IOException`/`HttpException`,
so callers are likely not prepared to catch it.

**Fix:**  
Validate in `RetryConfig.fromConfig` (or in the constructor via `require`):

```scala
def fromConfig(config: Config): RetryConfig = {
  val maxRetries   = if (config.hasPath(MaxRetriesKey))   config.getInt(MaxRetriesKey)   else Default.maxRetries
  val initialDelay = if (config.hasPath(InitialDelayKey)) config.getLong(InitialDelayKey) else Default.initialDelay
  val maxDelay     = if (config.hasPath(MaxDelayKey))     config.getLong(MaxDelayKey)     else Default.maxDelay

  require(maxRetries   >= 0,    s"maxRetries must be >= 0, got $maxRetries")
  require(initialDelay >  0L,   s"initialDelay must be > 0 ms, got $initialDelay")
  require(maxDelay     >= initialDelay,
    s"maxDelay ($maxDelay) must be >= initialDelay ($initialDelay)")

  RetryConfig(maxRetries, initialDelay, maxDelay)
}
```

---

### [CRITICAL] `computeDelay` overflow guard is incomplete — `Thread.sleep` receives a negative value

**File:** `agent/src/main/scala/za/co/absa/atum/agent/dispatcher/HttpDispatcher.scala:181–187`

**Issue:**  
The guard `val safeAttempt = math.min(attempt, 62)` prevents `1L << 63 = Long.MinValue`
(the comment is correct about that). However, the subsequent **multiplication** is unguarded:

```scala
val expDelay = retryConfig.initialDelay * (1L << safeAttempt)
```

For `safeAttempt = 62`, `1L << 62 = 4_611_686_018_427_387_904L`. With
`initialDelay = 3L` (a perfectly plausible test or custom value):

```
3L * 4_611_686_018_427_387_904L = 13_835_058_055_282_163_712L
                                 > Long.MaxValue (9_223_372_036_854_775_807L)
```

The product wraps to a **negative Long** in two's-complement arithmetic.
`math.min(negative, maxDelay)` then returns the negative value unchanged (it is less than any
positive `maxDelay`), and `Thread.sleep(negative)` throws `java.lang.IllegalArgumentException`.

This path is only reachable when `maxRetries` is configured higher than ~60, which is not
the default — but the safeAttempt guard was added explicitly to handle high-retry scenarios,
so the omission is an incomplete fix.

**Impact:** `IllegalArgumentException` thrown inside a retry loop, crashing the agent thread
for any deployment with `maxRetries > 62` and an `initialDelay` value whose product with
`2^62` overflows Long.

**Fix:**  
Cap `expDelay` before `math.min` to avoid overflow, or clamp directly to `maxDelay`:

```scala
private def computeDelay(attempt: Int): Long = {
  val safeAttempt = math.min(attempt, 62)
  // Use Long division to compute whether the product would exceed maxDelay before multiplying
  val cappedDelay =
    if (safeAttempt >= 0 &&
        retryConfig.initialDelay > 0L &&
        (1L << safeAttempt) > retryConfig.maxDelay / retryConfig.initialDelay)
      retryConfig.maxDelay
    else
      math.min(retryConfig.initialDelay * (1L << safeAttempt), retryConfig.maxDelay)
  val jitter = (retryRandom.nextDouble() * cappedDelay * 0.1).toLong
  cappedDelay + jitter
}
```

A simpler alternative is to cap at `maxDelay` before the multiplication is attempted
by checking `(1L << safeAttempt) > maxDelay / initialDelay` first.

---

### [CRITICAL] `null` assigned to a non-nullable typed `val` — NPE latent in `withRetry`

**File:** `agent/src/main/scala/za/co/absa/atum/agent/dispatcher/HttpDispatcher.scala:220–236`

**Issue:**  
The IOException retry branch uses `null` as a sentinel:

```scala
val response: Response[Either[String, String]] =
  try {
    backend.send(request)
  } catch {
    case e: java.io.IOException =>
      ...
      null   // sentinel: continue the while loop for the next attempt
  }
```

`Response[Either[String, String]]` is a Scala class with no nullability annotation. The
type declaration claims the value is always a valid `Response`, but the implementation assigns
`null`. The only thing preventing a NullPointerException is the manual `if (response != null)`
check four lines later (line 238).

This is a correctness time-bomb: any refactoring that moves, inlines, or reorganises the
null-check will produce a NPE on the next IOException retry. The Scala compiler gives no
warning because `null` is valid for any JVM reference type — the type system offers no
protection here.

**Impact:** Not currently a crash, but a single-line refactor mistake produces a runtime NPE
on the retry path. The pattern is non-idiomatic Scala and actively misleads readers about the
invariant "response is always a valid response object at this point in the loop."

**Fix:**  
Replace the null sentinel with a proper control-flow structure. Idiomatic options:

```scala
// Option A: use a Boolean flag (minimal change)
var shouldRetry = false
try {
  backend.send(request)
} catch {
  case e: java.io.IOException =>
    ...
    shouldRetry = true
    null.asInstanceOf[Response[...]] // still bad; prefer option B
}

// Option B: extract into a helper returning Option[Response]
private def trySend(
  request: Request[Either[String, String], capabilities.WebSockets]
): Either[java.io.IOException, Response[Either[String, String]]] =
  try Right(backend.send(request))
  catch { case e: java.io.IOException => Left(e) }
```

With `Option B`, `withRetry` matches on `Left`/`Right` rather than checking for `null`,
and the Scala type system enforces the invariant.

---

### [CRITICAL] Post-loop throw branches in `withRetry` are unreachable dead code

**File:** `agent/src/main/scala/za/co/absa/atum/agent/dispatcher/HttpDispatcher.scala:257–259`

**Issue:**  
After the `while` loop, the code reads:

```scala
// Reachable only when the loop exhausted via IOException path
if (lastIoException != null) throw lastIoException
throw new IllegalStateException("withRetry exited without returning a response")
```

The comment is incorrect — this code is **never reachable** under any input. Proof by
exhaustive analysis of all loop-exit paths:

1. **2xx / 3xx / 4xx response** → `return response` at line 252.
2. **5xx response, retries exhausted** (`attempt == maxRetries`) → `return response` at line 249.
3. **5xx response, retries remain** → `attempt += 1`, continue loop.
4. **IOException, retries remain** → `attempt += 1`, `response = null`, continue loop.
5. **IOException, retries exhausted** (`attempt == maxRetries`) → `throw e` at line 234,
   exception propagates out of `withRetry` immediately.
6. **Non-IOException exception** → propagates out immediately (not caught).

Path 5 is the only IOException-last-attempt case. It throws *before* any increment, so
`attempt` never exceeds `maxRetries`, the while condition `attempt <= maxRetries` never
becomes false, and the post-loop code is never reached.

**Impact:** The dead code represents an incorrect mental model of the control flow. The
`IllegalStateException` in path 5 above (Finding #1) appears to be a copy of the dead-code
guard here, which has led the author to believe this case *is* handled — but it is not (per
Finding #1, the dangerous unreachable case is when `maxRetries < 0`, not when the loop
exhausts normally).

The practical danger is that a future developer may rely on the post-loop `throw` as a
real safety net, remove one of the `return` statements, and introduce a missing-return
compile error they "fix" by removing the `throw` — eliminating the last line of defence.

**Fix:**  
Delete the dead code. Restructure the method to make the control flow explicit (see Fix
in Finding #3 for a skeleton). If a compile-time guarantee of exhaustiveness is desired,
restructure so the compiler can verify all paths return a `Response` without relying on
a fallthrough `throw`.

---

### [CRITICAL] `connectionTimeout` has no lower-bound guard; HikariCP minimum is 250 ms

**File:** `server/src/main/scala/za/co/absa/atum/server/config/PostgresConfig.scala:36`  
**Also affects:** `server/src/main/scala/za/co/absa/atum/server/api/database/TransactorProvider.scala:55`

**Issue:**  
`connectionTimeout: Long` is passed directly to `HikariConfig.setConnectionTimeout(long)`.
HikariCP's contract requires the value to be ≥ 250 ms:

> If this time is exceeded, and no connection becomes available, a `SQLException` will be
> thrown. Minimum value is 250ms.
> — HikariCP JavaDoc

HikariCP enforces this internally and throws a `HikariPool.PoolInitializationException`
with the message `"connectionTimeout cannot be less than 250ms"` if the value is violated.
There is no validation in `PostgresConfig`, `TransactorProvider`, or the test unit
`PostgresConfigUnitTests` that guards against this:

```scala
// TransactorProvider.scala:55 — raw passthrough, no guard
config.setConnectionTimeout(postgresConfig.connectionTimeout)
```

A misconfiguration of e.g. `connectionTimeout=100` causes the entire server to fail at
startup with an opaque HikariCP exception rather than a clear configuration error.

**Impact:** Server startup failure with a non-obvious error message. Operators who configure
milliseconds incorrectly (e.g., thinking the unit is seconds) get no domain-level feedback.

**Fix:**  
Add a `require` check at the point of use, or define a constant:

```scala
// TransactorProvider.scala
private val HikariMinConnectionTimeoutMs = 250L

// inside the ZLayer block, before setConnectionTimeout:
require(
  postgresConfig.connectionTimeout >= HikariMinConnectionTimeoutMs,
  s"postgres.connectionTimeout must be >= ${HikariMinConnectionTimeoutMs}ms " +
  s"(HikariCP minimum), got ${postgresConfig.connectionTimeout}ms"
)
config.setConnectionTimeout(postgresConfig.connectionTimeout)
```

Alternatively validate in a `PostgresConfig.validated` smart constructor and fail
with a `Config.Error` so ZIO's config layer surfaces it at the right layer.

---

_Reviewed: 2026-05-22_  
_Reviewer: gsd-code-reviewer (deep analysis, 24 files, 3 phases)_  
_All 5 Critical findings resolved in the same change set_
