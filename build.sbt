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
import Dependencies.Versions.{spark3, spark4}
import VersionAxes.*

ThisBuild / scalaVersion := Setup.scala213.asString

ThisBuild / versionScheme := Some("early-semver")

Global / onChangedBuildSource := ReloadOnSourceChanges

val limitedProject: Boolean = Setup.currentJava < Setup.recommendedJava

initialize := {
  val _ = initialize.value // Ensure previous initializations are run

  assert(
    Setup.currentJava >= Setup.spark3RequiredJava,
    s"Running on Java version ${Setup.currentJava}, required is at least version ${Setup.spark3RequiredJava}, recommended is ${Setup.recommendedJava}"
  )

  if (limitedProject) {
    val log = Keys.sLog.value
    log.warn(
      s"Some nodules will not be loaded, because they require at least Java ${Setup.recommendedJava} while Java ${Setup.currentJava} has been found"
    )
    log.warn("""Affected modules are: "atum-server", "atum-database"""")
  }

  if (!Setup.spark4Supported) {
    val log = Keys.sLog.value
    log.warn(
      s"The Spark ${Dependencies.Versions.spark4} rows of the 'atum-agent' module will not be loaded, because they require at least Java ${Setup.spark4RequiredJava} while Java ${Setup.currentJava} has been found"
    )
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
    .enablePlugins(GitVersioning)
    .enablePlugins(BuildInfoPlugin)
    .settings(
      Setup.commonSettings ++ Seq(
        name := "atum-server",
        javacOptions ++= Setup.serverAndDbJavacOptions,
        Compile / packageBin / publishArtifact := false,
        packageBin := (Compile / assembly).value,
        artifactPath / (Compile / packageBin) := baseDirectory.value / s"target/${name.value}-${version.value}.jar",
        testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
        Setup.serverMergeStrategy,
        publish / skip := true,
        version := git.gitDescribedVersion.value.map(_.takeWhile(_ != '-')).getOrElse("unknown"),
        buildInfoKeys := Seq[BuildInfoKey](
          version,
          "fullVersion" -> git.gitDescribedVersion.value.getOrElse("unknown")
        ),
        buildInfoPackage := "za.co.absa.atum.server.api.common.http"
      ): _*
    )
    .enablePlugins(AssemblyPlugin)
    .enablePlugins(AutomateHeaderPlugin)
    .addSingleScalaBuild(Setup.serverAndDbScalaVersion, Dependencies.serverDependencies)
    .dependsOn(model)
    .enablePlugins(JacocoFilterPlugin)

  if (limitedProject) {
    null // if value other then null is returned, the condition doesn't seem to work.
  } else {
    server
  }
}

/**
 * Module `agent` is the library to be plugged into the Spark application to measure the data and send it to the server
 *
 * It is cross-built per Spark major version, each axis carrying its own Scala and Java baseline:
 * - Spark 3.5.x -> Scala 2.12 + 2.13, Java 8  (`atum-agent-spark3_2.12`, `atum-agent-spark3_2.13`)
 * - Spark 4.0.x -> Scala 2.13,        Java 17 (`atum-agent-spark4_2.13`)
 *
 * The Spark 4 rows require a JDK 17+ to compile, so they are only added when sbt itself runs on one.
 */
lazy val agent = {
  val agent = (projectMatrix in file("agent"))
    .disablePlugins(sbtassembly.AssemblyPlugin)
    .settings(
      Setup.commonSettings ++ Seq(
        name := "atum-agent"
      ): _*
    )
    .addSparkCrossBuild(
      SparkVersionAxis(spark3),
      Setup.clientSupportedScalaVersions(spark3),
      Dependencies.agentDependencies
    )

  val agentWithSpark4 = if (Setup.spark4Supported) {
    agent.addSparkCrossBuild(
      SparkVersionAxis(spark4),
      Setup.clientSupportedScalaVersions(spark4),
      Dependencies.agentDependencies
    )
  } else {
    agent
  }

  agentWithSpark4
    .dependsOn(model)
    .enablePlugins(JacocoFilterPlugin)
}

/**
 * Module `model` is the data model for data exchange with server
 */
lazy val model = (projectMatrix in file("model"))
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings(
    Setup.commonSettings ++ Seq(
      name         := "atum-model",
      javacOptions ++= Setup.clientJavacOptions()
    ): _*
  )
  .addScalaCrossBuild(Setup.clientSupportedScalaVersions, Dependencies.modelDependencies)
  .enablePlugins(JacocoFilterPlugin)

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
      javacOptions ++= Setup.clientJavacOptions()
    ): _*
  )
  .addScalaCrossBuild(Setup.clientSupportedScalaVersions, Dependencies.readerDependencies)
  .dependsOn(model)
  .enablePlugins(JacocoFilterPlugin)
