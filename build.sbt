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

import sbt.*
import sbt.Keys.*
import Dependencies.*
import Dependencies.Versions.spark3
import VersionAxes.*

ThisBuild / scalaVersion := Setup.scala213.asString

ThisBuild / versionScheme := Some("early-semver")

Global / onChangedBuildSource := ReloadOnSourceChanges

val limitedProject: Boolean = Setup.currentJava < Setup.recommendedJava

initialize := {
  val _ = initialize.value // Ensure previous initializations are run

  assert(Setup.currentJava >= Setup.requiredJava,
    s"Running on Java version ${Setup.currentJava}, required is at least version ${Setup.requiredJava}, recommended is ${Setup.recommendedJava}")

  if (limitedProject) {
    val log = Keys.sLog.value
    log.warn(s"Some nodules will not be loaded, because they require at least Java ${Setup.recommendedJava} while Java ${Setup.currentJava} has been found")
    log.warn("""Affected modules are: "atum-server", "atum-database"""")
  }
}

enablePlugins(FlywayPlugin)
flywayUrl := FlywayConfiguration.flywayUrl
flywayUser := FlywayConfiguration.flywayUser
flywayPassword := FlywayConfiguration.flywayPassword
flywayLocations := FlywayConfiguration.flywayLocations
flywaySqlMigrationSuffixes := FlywayConfiguration.flywaySqlMigrationSuffixes
flywayBaselineVersion := FlywayConfiguration.flywayBaselineVersion
libraryDependencies ++= flywayDependencies

/**
 * Module `server` is the service application that collects and stores measured data And upo request retrives them
 */
lazy val server = {
  val server = (projectMatrix in file("server"))
    .settings(
      Setup.commonSettings ++ Seq(
        name := "atum-server",
        javacOptions ++= Setup.serverAndDbJavacOptions,
        Compile / packageBin / publishArtifact := false,
        packageBin := (Compile / assembly).value,
        artifactPath / (Compile / packageBin) := baseDirectory.value / s"target/${name.value}-${version.value}.jar",
        testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
        Setup.serverMergeStrategy,
        publish / skip := true
      ): _*
    )
    .enablePlugins(AssemblyPlugin)
    .enablePlugins(AutomateHeaderPlugin)
    .enablePlugins(FilteredJacocoAgentPlugin)
    .addSingleScalaBuild(Setup.serverAndDbScalaVersion, Dependencies.serverDependencies)
    .dependsOn(model)

  if (limitedProject) {
    null // if value other then null is returned, the condition doesn't seem to work.
  } else {
    server
  }
}

/**
 * Module `agent` is the library to be plugged into the Spark application to measure the data and send it to the server
 */
lazy val agent = (projectMatrix in file("agent"))
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings(
    Setup.commonSettings ++ Seq(
      name := "atum-agent",
      javacOptions ++= Setup.clientJavacOptions
    ): _*
  )
  .enablePlugins(FilteredJacocoAgentPlugin)
  .addSparkCrossBuild(SparkVersionAxis(spark3), Setup.clientSupportedScalaVersions, Dependencies.agentDependencies)
  .dependsOn(model)

/**
 * Module `model` is the data model for data exchange with server
 */
lazy val model = (projectMatrix in file("model"))
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings(
    Setup.commonSettings ++ Seq(
      name         := "atum-model",
      javacOptions ++= Setup.clientJavacOptions,
    ): _*
  )
  .enablePlugins(FilteredJacocoAgentPlugin)
  .addScalaCrossBuild(Setup.clientSupportedScalaVersions, Dependencies.modelDependencies)

/**
 * Module `database` is the source of database structures of the service
 */
lazy val database = {
  val database = (projectMatrix in file("database"))
    .disablePlugins(sbtassembly.AssemblyPlugin)
    .settings(
      Setup.commonSettings ++ Seq(
        name := "atum-database",
        javacOptions ++= Setup.serverAndDbJavacOptions,
        publish / skip := true
      ): _*
    )
    .enablePlugins(FilteredJacocoAgentPlugin)
    .addSingleScalaBuild(Setup.serverAndDbScalaVersion, Dependencies.databaseDependencies)
  if (limitedProject) {
    null // if value other then null is returned, the condition doesn't seem to work.
  } else {
    database
  }
}

/**
 * Module `reader` is the library to be plugged into application which wants to easily read the measured data stored on
 * the server
 */
lazy val reader = (projectMatrix in file("reader"))
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings(
    Setup.commonSettings ++ Seq(
      name := "atum-reader",
      javacOptions ++= Setup.clientJavacOptions
    ): _*
  )
  .enablePlugins(FilteredJacocoAgentPlugin)
  .addScalaCrossBuild(Setup.clientSupportedScalaVersions, Dependencies.readerDependencies)
  .dependsOn(model)

// Run activate jacoco + clean + test + per-module reports across the whole build + deactivate jacoco
addCommandAlias("jacoco", "; jacocoOn; clean; test; jacocoReportAll; jacocoOff")
addCommandAlias("jacocoOff",  "; set every jacocoPluginEnabled := false")
addCommandAlias("jacocoOn",   "; set every jacocoPluginEnabled := true")
