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

import Dependencies.*
import SparkVersionAxis.*
import JacocoSetup.*
import sbt.Keys.name


ThisBuild / organization := "za.co.absa"

ThisBuild / scalaVersion := Versions.scala212  // default version

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
  javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint"),
  Test / parallelExecution := false
)

val mergeStrategy: Def.SettingsDefinition = assembly / assemblyMergeStrategy := {
  case PathList("META-INF", _) => MergeStrategy.discard
  case "application.conf"      => MergeStrategy.concat
  case "reference.conf"        => MergeStrategy.concat
  case _                       => MergeStrategy.first
}

enablePlugins(FlywayPlugin)
flywayUrl := "jdbc:postgresql://localhost:5432/atum_db"
flywayUser := "postgres"
flywayPassword := "postgres"
flywayLocations := Seq("filesystem:database/src/main/postgres")
flywaySqlMigrationSuffixes := Seq(".sql",".ddl")
libraryDependencies += "org.postgresql" % "postgresql" % "42.5.4"

lazy val root = (projectMatrix in file("."))
  .aggregate(model, server, agent)
  .settings(
    name := "atum-service-root",
    javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint"),
    publish / skip := true,
    mergeStrategy
  )

lazy val server = (projectMatrix in file("server"))
  .settings(
    commonSettings ++ Seq(
      name := "atum-server",
      libraryDependencies ++= Dependencies.serverDependencies,
      scalacOptions ++= Seq("-Ymacro-annotations"),
      Compile / packageBin / publishArtifact := false,
      (Compile / compile) := ((Compile / compile) dependsOn printSparkScalaVersion).value,
      packageBin := (Compile / assembly).value,
      artifactPath / (Compile / packageBin) := baseDirectory.value / s"target/${name.value}-${version.value}.war",
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
  .enablePlugins(TomcatPlugin)
  .enablePlugins(AutomateHeaderPlugin)
  .jvmPlatform(scalaVersions = Seq(Versions.serviceScalaVersion))
  .dependsOn(model)

lazy val agent = (projectMatrix in file("agent"))
  .settings(
    commonSettings ++ Seq(
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
    commonSettings ++ Seq(
      name         := "atum-model",
      libraryDependencies ++= Dependencies.modelDependencies(scalaVersion.value),
      (Compile / compile) := ((Compile / compile) dependsOn printSparkScalaVersion).value,
    ): _*
  )
  .settings(
    jacocoReportSettings := jacocoSettings(scalaVersion.value, "atum-agent: model"),
    jacocoExcludes := jacocoProjectExcludes()
  )
  .jvmPlatform(scalaVersions = Versions.clientSupportedScalaVersions)

//testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
