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

package za.co.absa.atum.server.api.common.http

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import zio.ZIO

import java.io.StringWriter

object HikariMetrics {

  // Separate registry for Hikari. It uses old Prometheus client under the hood - that's
  // why this need to be a separated endpoint, not shared with http4s metrics)
  val hikariRegistry: CollectorRegistry = new CollectorRegistry()

  // Logic to export metrics from the Hikari registry
  val getMetrics: ZIO[Any, Throwable, String] = ZIO.attempt {
    val writer = new StringWriter()
    TextFormat.write004(writer, hikariRegistry.metricFamilySamples())
    writer.toString
  }

}
