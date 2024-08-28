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

import sbt.Keys.name
import sbt.*
import Dependencies.*
import Dependencies.Versions.spark3
import VersionAxes.*

ThisBuild / scalaVersion := Setup.scala213.asString  // default version TODO

ThisBuild / versionScheme := Some("early-semver")

Global / onChangedBuildSource := ReloadOnSourceChanges

initialize := {
  val _ = initialize.value // Ensure previous initializations are run

  val requiredJavaVersion = VersionNumber("11")
  val currentJavaVersion = VersionNumber(sys.props("java.specification.version"))
  println(s"Running on Java version $currentJavaVersion, required is at least version $requiredJavaVersion")
  //this routine can be used to assert the required Java version
}

enablePlugins(FlywayPlugin)
flywayUrl := FlywayConfiguration.flywayUrl
flywayUser := FlywayConfiguration.flywayUser
flywayPassword := FlywayConfiguration.flywayPassword
flywayLocations := FlywayConfiguration.flywayLocations
flywaySqlMigrationSuffixes := FlywayConfiguration.flywaySqlMigrationSuffixes
libraryDependencies ++= flywayDependencies

/**
 * Module `server` is the service application that collects and stores measured data And upo request retrives them
 */
lazy val server = (projectMatrix in file("server"))
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
  .addSingleScalaBuild(Setup.serverAndDbScalaVersion, Dependencies.serverDependencies)
  .dependsOn(model)

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
  .addScalaCrossBuild(Setup.clientSupportedScalaVersions, Dependencies.modelDependencies)

/**
 * Module `database` is the source of database structures of the service
 */
lazy val database = (projectMatrix in file("database"))
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings(
    Setup.commonSettings ++ Seq(
      name := "atum-database",
      javacOptions ++= Setup.serverAndDbJavacOptions,
      publish / skip := true
    ): _*
  )
  .addSingleScalaBuild(Setup.serverAndDbScalaVersion, Dependencies.databaseDependencies)

/**
 * Module `reader` is the library to be plugged into application which wants to easily read the measured data stored on
 * the server
 */
lazy val reader = (projectMatrix in file("reader"))
  .disablePlugins(sbtassembly.AssemblyPlugin)
  .settings(
    Setup.commonSettings ++ Seq(
      name := "atum-reader",
      javacOptions ++= Setup.clientJavacOptions,
      publish / skip := true // module is not yet finished, so we don't want to publish it
    ): _*
  )
  .addScalaCrossBuild(Setup.clientSupportedScalaVersions, Dependencies.readerDependencies)
  .dependsOn(model)
