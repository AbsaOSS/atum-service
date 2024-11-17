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

import sbt.{Def, ModuleID, VirtualAxis, *}
import sbt.Keys.*
import sbt.internal.ProjectMatrix
import JacocoSetup.jacocoSettings
import com.github.sbt.jacoco.JacocoKeys.jacocoReportSettings
import za.co.absa.commons.version.Version


object VersionAxes {

  lazy val printVersionInfo = taskKey[Unit]("Print main component versions atum-service is being built for.")

  case class SparkVersionAxis(sparkVersion: String) extends sbt.VirtualAxis.WeakAxis {

    val sparkVersionMajor: String = Dependencies.Versions.getVersionUpToMajor(sparkVersion)
    override val directorySuffix = s"-spark$sparkVersionMajor"
    override val idSuffix: String = directorySuffix.replaceAll("""\W+""", "_")
  }

  private def camelCaseToLowerDashCase(origName: String): String = {
    origName
      .replaceAll("([A-Z])", "-$1")
      .toLowerCase()
  }

  implicit class ProjectExtension(val projectMatrix: ProjectMatrix) extends AnyVal {

    def addSparkCrossBuild(sparkAxis: SparkVersionAxis,
                           scalaVersions: Seq[Version],
                           dependenciesFnc: (String, Version) => Seq[ModuleID],
                           settings: Def.SettingsDefinition*): ProjectMatrix = {
      val sparkVersion = sparkAxis.sparkVersion
      scalaVersions.foldLeft(projectMatrix) { case (currentProjectMatrix, scalaVersion) =>
        currentProjectMatrix.customRow(
          scalaVersions = Seq(scalaVersion.asString),
          axisValues = Seq(sparkAxis, VirtualAxis.jvm),
          _.settings(
            moduleName := camelCaseToLowerDashCase(name.value + sparkAxis.directorySuffix),
            scalacOptions ++= Setup.clientScalacOptions(scalaVersion),
            libraryDependencies ++= dependenciesFnc(sparkVersion, scalaVersion),
            printVersionInfo := streams.value.log.info(s"Building ${name.value} with Spark $sparkVersion, Scala ${scalaVersion.asString}"),
            (Compile / compile) := ((Compile / compile) dependsOn printVersionInfo).value,
            jacocoReportSettings := jacocoSettings(sparkVersion, scalaVersion, name.value),
          ).settings(settings *)
        )
      }
    }

    def addScalaCrossBuild(scalaVersions: Seq[Version],
                           dependenciesFnc: Version => Seq[ModuleID],
                           settings: Def.SettingsDefinition*): ProjectMatrix = {
      scalaVersions.foldLeft(projectMatrix)((currentProjectMatrix, scalaVersion) =>
        currentProjectMatrix.customRow(
          scalaVersions = Seq(scalaVersion.asString),
          axisValues = Seq(VirtualAxis.jvm),
          _.settings(
            scalacOptions ++= Setup.clientScalacOptions(scalaVersion),
            libraryDependencies ++= dependenciesFnc(scalaVersion),
            printVersionInfo := streams.value.log.info(s"Building ${name.value} with Scala ${scalaVersion.asString}"),
            (Compile / compile) := ((Compile / compile) dependsOn printVersionInfo).value,
            jacocoReportSettings := jacocoSettings(scalaVersion, name.value),
          ).settings(settings *)
        )
      )
    }

    def addSingleScalaBuild(scalaVersion: Version,
                            dependenciesFnc: => Seq[ModuleID],
                            settings: Def.SettingsDefinition*): ProjectMatrix = {
      projectMatrix.customRow(
        scalaVersions = Seq(scalaVersion.asString),
        axisValues = Seq(VirtualAxis.jvm),
        _.settings(
          libraryDependencies ++= dependenciesFnc,
          scalacOptions ++= Setup.serverAndDbScalacOptions,
          printVersionInfo := streams.value.log.info(s"Building ${name.value} with Scala ${scalaVersion.asString}"),
          (Compile / compile) := ((Compile / compile) dependsOn printVersionInfo).value,
          jacocoReportSettings := jacocoSettings(scalaVersion, name.value),
        ).settings(settings *)
      )
    }

  }
}

