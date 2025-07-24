/*
 * Copyright 2021 ABSA Group Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.github.sbt.jacoco.JacocoKeys.JacocoReportFormats
import com.github.sbt.jacoco.report.JacocoReportSettings
import za.co.absa.commons.version.Version

object JacocoSetup {

  private val jacocoReportCommonSettings: JacocoReportSettings = JacocoReportSettings(
    formats = Seq(JacocoReportFormats.HTML, JacocoReportFormats.XML)
  )

  def jacocoSettings(sparkVersion: String, scalaVersion: Version, moduleName: String): JacocoReportSettings = {
    jacocoReportCommonSettings.withTitle(
      s"Jacoco Report on `$moduleName` for spark:$sparkVersion - scala:${scalaVersion.asString}"
    )
  }

  def jacocoSettings(scalaVersion: Version, moduleName: String): JacocoReportSettings = {
    jacocoReportCommonSettings.withTitle(s"Jacoco Report on `$moduleName` for scala:${scalaVersion.asString}")
  }

  def jacocoProjectExcludes(): Seq[String] = {
    Seq(
      "**.api.http.*",
      "**.config.*",
      "za.co.absa.atum.agent.dispatcher.Dispatcher",
      "za.co.absa.atum.agent.dispatcher.ConsoleDispatcher",
      "za.co.absa.atum.server.Main*",
      "za.co.absa.atum.server.Constants*",
      "za.co.absa.atum.server.api.database.DoobieImplicits*",
      "za.co.absa.atum.server.api.database.TransactorProvider*",
      "za.co.absa.atum.server.api.common.http.Routes*",
      "za.co.absa.atum.server.api.v2.repository.PartitioningRepository",
      "za.co.absa.atum.server.api.v2.repository.PartitioningRepository$",
      "**.v2.controller.PartitioningController*",
      "za.co.absa.atum.server.api.v2.service.PartitioningService.**",
      "za.co.absa.atum.model.envelopes.Pagination",
      "za.co.absa.atum.model.dto.PartitioningParentPatchDTO*",
      "za.co.absa.atum.model.ApiPaths*",
      "za.co.absa.atum.model.envelopes.ResponseEnvelope",
      "za.co.absa.atum.model.envelopes.StatusResponse",
      "za.co.absa.atum.model.envelopes.SuccessResponse"
    )
  }

}
