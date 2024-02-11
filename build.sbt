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

import Dependencies._
import SparkVersionAxis._
import JacocoSetup._
import sbt.Keys.name


ThisBuild / organization := "za.co.absa"

ThisBuild / scalaVersion := Versions.scala213  // default version

ThisBuild / versionScheme := Some("early-semver")

Global / onChangedBuildSource := ReloadOnSourceChanges


lazy val printSparkScalaVersion = taskKey[Unit]("Print Spark and Scala versions for atum-service is being built for.")
ThisBuild / printSparkScalaVersion := {
  val log = streams.value.log
  val sparkVer = sparkVersionForScala(scalaVersion.value)
  log.info(s"Building with Spark $sparkVer, Scala ${scalaVersion.value}")
}

lazy val commonSettings = Seq(
  libraryDependencies ++= commonDependencies,
  scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Xfatal-warnings"),
  Test / parallelExecution := false
)

val mergeStrategy: Def.SettingsDefinition = assembly / assemblyMergeStrategy := {
  case PathList("META-INF", _) => MergeStrategy.discard
  case "application.conf"      => MergeStrategy.concat
  case "reference.conf"        => MergeStrategy.concat
  case _                       => MergeStrategy.first
}

enablePlugins(FlywayPlugin)
flywayUrl := FlywayConfiguration.flywayUrl
flywayUser := FlywayConfiguration.flywayUser
flywayPassword := FlywayConfiguration.flywayPassword
flywayLocations := FlywayConfiguration.flywayLocations
flywaySqlMigrationSuffixes := FlywayConfiguration.flywaySqlMigrationSuffixes
libraryDependencies ++= flywayDependencies

lazy val root = (projectMatrix in file("."))
  .aggregate(model, server, agent)
  .settings(
    name := "atum-service-root",
    publish / skip := true,
    mergeStrategy
  )

lazy val server = (projectMatrix in file("server"))
  .settings(
    commonSettings,
      Seq(
        scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Xfatal-warnings"),
        scalacOptions ++= Seq("-release", "11"),
        javacOptions ++= Seq("-source", "11", "-target", "11", "-Xlint"),
        Test / parallelExecution := false,
        assembly / assemblyMergeStrategy := {
        case PathList("META-INF", "maven", "org.webjars", "swagger-ui", "pom.properties") => MergeStrategy.singleOrError
        case PathList("META-INF", "resources", "webjars", "swagger-ui", _*)               => MergeStrategy.singleOrError
        case PathList("META-INF", _*)                                                     => MergeStrategy.discard
        case PathList("META-INF", "versions", "9", xs @ _*)                               => MergeStrategy.discard
        case PathList("module-info.class")                                                => MergeStrategy.discard
        case "application.conf"      => MergeStrategy.concat
        case "reference.conf"        => MergeStrategy.concat
        case _                       => MergeStrategy.first
        },
      name := "atum-server",
      libraryDependencies ++= Dependencies.serverDependencies ++ commonDependencies,
      scalacOptions ++= Seq("-Ymacro-annotations"),
      Compile / packageBin / publishArtifact := false,
      (Compile / compile) := ((Compile / compile) dependsOn printSparkScalaVersion).value,
      packageBin := (Compile / assembly).value,
      artifactPath / (Compile / packageBin) := baseDirectory.value / s"target/${name.value}-${version.value}.jar",
      webappWebInfClasses := true,
      inheritJarManifest := true,
      testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
    ): _*
  )
  .settings(
    jacocoReportSettings := jacocoSettings(scalaVersion.value, "atum-server"),
    jacocoExcludes := jacocoProjectExcludes()
  )
  .enablePlugins(AssemblyPlugin)
  .enablePlugins(AutomateHeaderPlugin)
  .jvmPlatform(scalaVersions = Seq(Versions.serviceScalaVersion))
  .dependsOn(model)

lazy val agent = (projectMatrix in file("agent"))
  .settings(
    commonSettings,
    scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, major)) if major >= 12 => Seq("-release", "8")
      case _ => Seq("-target:jvm-1.8")
    }),
    javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint"),
    Seq(
      name := "atum-agent",
      libraryDependencies ++= Dependencies.agentDependencies(
        if (scalaVersion.value == Versions.scala211) Versions.spark2 else Versions.spark3,
        scalaVersion.value
      ),
      (Compile / compile) := ((Compile / compile) dependsOn printSparkScalaVersion).value,
      mergeStrategy
    ): _*
  )
  .settings(
    jacocoReportSettings := jacocoSettings(scalaVersion.value, "atum-agent"),
    jacocoExcludes := jacocoProjectExcludes()
  )
  .sparkRow(SparkVersionAxis(Versions.spark2), scalaVersions = Seq(Versions.scala211, Versions.scala212))
  .sparkRow(SparkVersionAxis(Versions.spark3), scalaVersions = Seq(Versions.scala212, Versions.scala213))
  .jvmPlatform(scalaVersions = Versions.clientSupportedScalaVersions)
  .dependsOn(model)

lazy val model = (projectMatrix in file("model"))
  .settings(
    commonSettings,
    scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, major)) if major >= 12 => Seq("-release", "8")
      case _ => Seq("-target:jvm-1.8")
    }),
    javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint"),
    Seq(
      name         := "atum-model",
      libraryDependencies ++= Dependencies.modelDependencies(scalaVersion.value),
      (Compile / compile) := ((Compile / compile) dependsOn printSparkScalaVersion).value,
    ): _*
  )
  .settings(
    jacocoReportSettings := jacocoSettings(scalaVersion.value, "atum-model: model"),
    jacocoExcludes := jacocoProjectExcludes()
  )
  .jvmPlatform(scalaVersions = Versions.clientSupportedScalaVersions)
