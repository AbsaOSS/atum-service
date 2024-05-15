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

ThisBuild / organization := "za.co.absa.atum-service"
sonatypeProfileName := "za.co.absa"

ThisBuild / scalaVersion := Setup.scala213  // default version

ThisBuild / versionScheme := Some("early-semver")

Global / onChangedBuildSource := ReloadOnSourceChanges

publish / skip := true

initialize := {
  val _ = initialize.value // Ensure previous initializations are run

  val requiredJavaVersion = VersionNumber("17")
  val current = VersionNumber(sys.props("java.specification.version"))
  // Assert that the JVM meets the minimum required version, for example, Java 17
  //assert(specVersion.toDouble >= 17, "Java 17 or above is required to run this project.")
  println(s"Running on Java version $current")
}

lazy val commonSettings = Seq(
  scalacOptions ++= Setup.commonScalacOptions,
  Test / parallelExecution := false,
  jacocoExcludes := JacocoSetup.jacocoProjectExcludes()
)


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
    commonSettings ++ Seq(
      name := "atum-server",
      javacOptions ++= Setup.serviceJavacOptions,
      Compile / packageBin / publishArtifact := false,
      packageBin := (Compile / assembly).value,
      artifactPath / (Compile / packageBin) := baseDirectory.value / s"target/${name.value}-${version.value}.jar",
      testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
      Setup.serverMergeStrategy
    ): _*
  )
  .enablePlugins(AssemblyPlugin)
  .enablePlugins(AutomateHeaderPlugin)
  .singleRow(Setup.serviceScalaVersion, Dependencies.serverDependencies)
  .dependsOn(model)

/**
 * Module `agent` is the library to be plugged into the Spark application to measure the data and send it to the server
 */
lazy val agent = (projectMatrix in file("agent"))
  .settings(
    commonSettings ++ Seq(
      name := "atum-agent",
      javacOptions ++= Setup.clientJavacOptions
    ): _*
  )
  .sparkRow(SparkVersionAxis(spark3), Setup.clientSupportedScalaVersions, Dependencies.agentDependencies)
  .dependsOn(model)

/**
 * Module `model` is the data model for data exchange with server
 */
lazy val model = (projectMatrix in file("model"))
  .settings(
    commonSettings ++ Seq(
      name         := "atum-model",
      javacOptions ++= Setup.clientJavacOptions,
    ): _*
  )
  .scalasRow(Setup.clientSupportedScalaVersions, Dependencies.modelDependencies)

/**
 * Module `database` is the source of database structures of the service
 */
lazy val database = (projectMatrix in file("database"))
  .settings(
    commonSettings ++ Seq(
      name := "atum-database",
      javacOptions ++= Setup.serviceJavacOptions,
      test := {}
    ): _*
  )
  .singleRow(Setup.serviceScalaVersion, Dependencies.databaseDependencies)

//----------------------------------------------------------------------------------------------------------------------
lazy val dbTest = taskKey[Unit]("Launch DB tests")

dbTest := {
  println("Running DB tests")
  (database.jvm(Setup.serviceScalaVersion) / Test / test).value
}
