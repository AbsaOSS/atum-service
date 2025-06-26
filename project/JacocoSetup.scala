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
import sbt.*
import sbt.Keys.*

import scala.sys.process.*
import za.co.absa.commons.version.Version

import java.nio.file.{Files, StandardCopyOption}

object JacocoSetup {

  private val JacocoCLI = config("jacococli").hide

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
      "za.co.absa.atum.model.envelopes.Pagination",
      "za.co.absa.atum.model.envelopes.ResponseEnvelope",
      "za.co.absa.atum.model.envelopes.StatusResponse",
      "za.co.absa.atum.model.envelopes.SuccessResponse"
    )
  }

  // Injectable task
  def filterJacocoTask: Def.Initialize[Task[Unit]] = Def.task {
    val log = streams.value.log
    val os = sys.props("os.name").toLowerCase
    val isWindows = os.contains("win")
    val isMac = os.contains("mac")
    val isLinux = os.contains("linux")

    val zipName = if (isWindows) {
      "jacoco-filter-Windows.zip"
    } else if (isMac) {
      "jacoco-filter-macOS.zip"
    } else if (isLinux) {
      "jacoco-filter-Linux.zip"
    } else {
      sys.error(s"Unsupported OS: $os")
    }

    val downloadUrl = s"https://github.com/MoranaApps/jacoco-filter/releases/latest/download/$zipName"
    val targetDir = target.value
    val zipPath = targetDir / zipName
    val binaryName = zipName.replace(".zip", "")
    val binaryPath = targetDir / binaryName / "jacoco-filter" / binaryName.toLowerCase()

    if (!binaryPath.exists()) {
      log.info(s"Downloading $zipName...")

      val connection = new URL(downloadUrl).openConnection()
      val inputStream = connection.getInputStream
      Files.copy(inputStream, zipPath.toPath, StandardCopyOption.REPLACE_EXISTING)
      inputStream.close()

      log.info("Unzipping binary...")
      IO.unzip(zipPath, targetDir)

      if (!isWindows) {
        binaryPath.setExecutable(true)
      }
    } else {
      log.info(s"Binary already exists: $binaryPath")
    }

    val tomlPath = baseDirectory.value / "jacoco_filter.toml"
    if (!tomlPath.exists()) {
      sys.error(s"Config file not found: $tomlPath")
    }

    log.info(s"Running jacoco-filter with config: $tomlPath")

    val cmd = if (isWindows)
      Seq("cmd", "/c", binaryPath.getAbsolutePath, "--config", tomlPath.getAbsolutePath)
    else
      Seq(binaryPath.getAbsolutePath, "--config", tomlPath.getAbsolutePath)

    val exitCode = Process(cmd).!

    if (exitCode != 0)
      sys.error(s"jacoco-filter failed with exit code $exitCode")
  }
}
