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

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

object JacocoSetup {

  private val jacocoReportCommonSettings: JacocoReportSettings = JacocoReportSettings(
    formats = Seq(JacocoReportFormats.HTML, JacocoReportFormats.XML)
  )

  def jacocoSettings(sparkVersion: String, scalaVersion: String): JacocoReportSettings = {
    val utcDateTime = ZonedDateTime.now.withZoneSameInstant(ZoneId.of("UTC"))
    val now = s"as of ${DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm Z z").format(utcDateTime)}"
    jacocoReportCommonSettings.withTitle(s"Jacoco Report on `spark-commons` for spark:$sparkVersion - scala:$scalaVersion [$now]")
  }

  def jacocoProjectExcludes(sparkVersion: String, scalaVersion: String): Seq[String] = {
    Seq(
      "za.co.absa.atum.service.adapters.CallUdfAdapter",
      "za.co.absa.atum.service.adapters.TransformAdapter"
    )
  }

}
