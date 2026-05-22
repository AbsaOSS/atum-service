/*
 * Copyright 2021 ABSA Group Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package za.co.absa.atum.agent.dispatcher

import com.typesafe.config.Config

/**
 *  Retry configuration and execution for [[HttpDispatcher]].
 *
 *  All three parameters are optional in the config: if the `atum.dispatcher.http.retry` block is absent
 *  the [[HttpRetry.Default]] values are used and no `ConfigException` is thrown.
 *
 *  @param maxRetries   Maximum number of retry attempts after the first failure (0 = no retries).
 *  @param initialDelay Initial backoff delay in milliseconds; doubles on every subsequent attempt.
 *  @param maxDelay     Upper cap for the computed backoff delay in milliseconds.
 */
case class HttpRetry(
  maxRetries: Int,
  initialDelay: Long,
  maxDelay: Long
) {

  private val random = new scala.util.Random()

  // Maximum exponent to prevent Long overflow when computing initialDelay * 2^attempt
  private val MaxExponent = 30
  // Fraction of the delay added as random jitter (0.1 = up to 10% extra).
  // When many agents fail simultaneously, jitter spreads their retries over a short window instead of all hitting the
  // server at the same instant.
  private val JitterFraction = 0.1

  /**
   *  Computes the backoff delay for a given attempt using exponential backoff with jitter.
   *
   *  @param attempt Zero-based attempt index (0 = first retry delay, 1 = second, ...).
   *  @return Delay in milliseconds to sleep before the next attempt.
   */
  private[dispatcher] def computeDelay(attempt: Int): Long = {
    val exponential = initialDelay * math.pow(2, math.min(attempt, MaxExponent)).toLong
    val cappedDelay = math.min(exponential, maxDelay)
    cappedDelay + (random.nextDouble() * cappedDelay * JitterFraction).toLong
  }

  /**
   *  Executes `action`, retrying on transient failures with exponential backoff.
   *
   *  Retry conditions:
   *   - `isRetryable(result)` returns true (e.g. HTTP 5xx)
   *   - `java.io.IOException` is thrown (network-level failure)
   *
   *  No-retry conditions (fail immediately):
   *   - `isRetryable(result)` returns false (e.g. 2xx, 4xx)
   *   - Any non-IOException exception
   *
   *  @param action      The by-name action to execute (called on each attempt).
   *  @param isRetryable Predicate on the result — true means the attempt should be retried.
   *  @param onRetry     Callback invoked before each retry sleep, receiving
   *                    (attemptNumber 1-based, totalAttempts, delayMs, reason).
   *  @return The result of the first non-retryable attempt, or the last result after retries exhausted.
   *  @throws java.io.IOException if all attempts result in an IOException.
   */
  private[dispatcher] def retry[T](
    action: => T
  )(isRetryable: T => Boolean)(onRetry: (Int, Int, Long, String) => Unit): T = {
    val totalAttempts = maxRetries + 1
    var attempt = 0

    while (true) {
      try {
        val result = action
        if (isRetryable(result) && attempt < maxRetries) {
          val delay = computeDelay(attempt)
          onRetry(attempt + 1, totalAttempts, delay, "retryable response")
          Thread.sleep(delay)
          attempt += 1
        } else {
          return result
        }
      } catch {
        case e: java.io.IOException =>
          if (attempt < maxRetries) {
            val delay = computeDelay(attempt)
            onRetry(attempt + 1, totalAttempts, delay, s"IOException: ${e.getMessage}")
            Thread.sleep(delay)
            attempt += 1
          } else {
            throw e
          }
      }
    }

    // Unreachable: while(true) always returns or throws, but the compiler cannot infer that.
    throw new IllegalStateException("retry loop exited without a result")
  }
}

object HttpRetry {

  private val MaxRetriesKey = "atum.dispatcher.http.retry.max-retries"
  private val InitialDelayKey = "atum.dispatcher.http.retry.initial-delay-ms"
  private val MaxDelayKey = "atum.dispatcher.http.retry.max-delay-ms"

  /** Sensible out-of-the-box defaults: 3 retries, 1 s initial delay, 10 s cap. */
  val Default: HttpRetry = HttpRetry(
    maxRetries = 3,
    initialDelay = 1000L,
    maxDelay = 10000L
  )

  /**
   *  Reads retry configuration from a Typesafe Config instance.
   *
   *  Each key is optional: if absent, the corresponding [[Default]] value is used.
   *  This design ensures backward-compatibility — existing configs that omit the
   *  `atum.dispatcher.http.retry` block will never throw a `ConfigException`.
   *
   *  @param config Typesafe Config, typically the application config passed to [[HttpDispatcher]].
   *  @return Populated [[HttpRetry]], falling back to [[Default]] for any absent key.
   */
  def fromConfig(config: Config): HttpRetry = {
    val maxRetries = if (config.hasPath(MaxRetriesKey)) config.getInt(MaxRetriesKey) else Default.maxRetries
    val initialDelay = if (config.hasPath(InitialDelayKey)) config.getLong(InitialDelayKey) else Default.initialDelay
    val maxDelay = if (config.hasPath(MaxDelayKey)) config.getLong(MaxDelayKey) else Default.maxDelay

    require(maxRetries >= 0, s"atum.dispatcher.http.retry.max-retries must be >= 0, got $maxRetries")
    require(initialDelay > 0, s"atum.dispatcher.http.retry.initial-delay-ms must be > 0, got $initialDelay")
    require(maxDelay > 0, s"atum.dispatcher.http.retry.max-delay-ms must be > 0, got $maxDelay")

    HttpRetry(maxRetries, initialDelay, maxDelay)
  }
}
