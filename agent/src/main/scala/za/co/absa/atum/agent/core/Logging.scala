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

package za.co.absa.atum.agent.core

import org.slf4j.{Logger, LoggerFactory}

/**
 *  Internal logging facility of the Atum Agent.
 *
 *  This deliberately replaces `org.apache.spark.internal.Logging`. That trait is a Spark-private API with no
 *  compatibility guarantees; depending on it risks `NoSuchMethodError` / `AbstractMethodError` at runtime on
 *  any given Spark point release, and Spark 4 in particular reworked it. slf4j is on every Spark classpath
 *  (Spark 3 ships slf4j 1.7.x, Spark 4 ships slf4j 2.x) and is binary stable across both, so the agent binds
 *  to it directly and stays portable across Spark major versions.
 *
 *  The logger is `@transient lazy` so that mixing this trait into a serializable class (e.g. anything captured
 *  by a Spark closure) does not make the class unserializable.
 */
trait Logging {

  /** Name the logger is registered under. Strips the trailing `$` that Scala appends to `object` class names. */
  protected def logName: String = this.getClass.getName.stripSuffix("$")

  @transient protected lazy val log: Logger = LoggerFactory.getLogger(logName)

}
