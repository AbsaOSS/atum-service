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
import JacocoSetup._
import sbt.Keys.name

ThisBuild / organization := "za.co.absa.atum-service"
sonatypeProfileName := "za.co.absa"

ThisBuild / scalaVersion := Versions.scala212  // default version

ThisBuild / versionScheme := Some("early-semver")

Global / onChangedBuildSource := ReloadOnSourceChanges

publish / skip := true

lazy val printSparkScalaVersion = taskKey[Unit]("Print Spark and Scala versions for atum-service is being built for.")
lazy val printScalaVersion = taskKey[Unit]("Print Scala versions for atum-service is being built for.")

lazy val commonSettings = Seq(
  libraryDependencies ++= commonDependencies,
  scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Xfatal-warnings"),
  javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint"),
  Test / parallelExecution := false,
  jacocoExcludes := jacocoProjectExcludes()
)

enablePlugins(FlywayPlugin)
flywayUrl := FlywayConfiguration.flywayUrl
flywayUser := FlywayConfiguration.flywayUser
flywayPassword := FlywayConfiguration.flywayPassword
flywayLocations := FlywayConfiguration.flywayLocations
flywaySqlMigrationSuffixes := FlywayConfiguration.flywaySqlMigrationSuffixes
libraryDependencies ++= flywayDependencies

lazy val server = (projectMatrix in file("server"))
  .settings(
    commonSettings ++ Seq(
      name := "atum-server",
      libraryDependencies ++= Dependencies.serverDependencies,
      scalacOptions ++= Seq("-Ymacro-annotations"),
      Compile / packageBin / publishArtifact := false,
      printScalaVersion := {
        val log = streams.value.log
        log.info(s"Building ${name.value} with Scala ${scalaVersion.value}")
      },
      (Compile / compile) := ((Compile / compile) dependsOn printScalaVersion).value,
      packageBin := (Compile / assembly).value,
      artifactPath / (Compile / packageBin) := baseDirectory.value / s"target/${name.value}-${version.value}.war",
      webappWebInfClasses := true,
      inheritJarManifest := true,
      publish / skip := true,
      jacocoReportSettings := jacocoSettings(scalaVersion.value, "atum-server")
    ): _*
  )
  .enablePlugins(AssemblyPlugin)
  .enablePlugins(TomcatPlugin)
  .enablePlugins(AutomateHeaderPlugin)
  .jvmPlatform(scalaVersions = Seq(Versions.serviceScalaVersion))
  .dependsOn(model)

lazy val agent = (projectMatrix in file("agent"))
  .settings(
    commonSettings ++ Seq(
      name := "agent",
      libraryDependencies ++= Dependencies.agentDependencies(
        if (scalaVersion.value == Versions.scala211) Versions.spark2 else Versions.spark3,
        scalaVersion.value
      ),
      printSparkScalaVersion := {
        val log = streams.value.log
        val sparkVer = sparkVersionForScala(scalaVersion.value)
        log.info(s"Building ${name.value} with Spark $sparkVer, Scala ${scalaVersion.value}")
      },
      (Compile / compile) := ((Compile / compile) dependsOn printSparkScalaVersion).value,
      jacocoReportSettings := jacocoSettings(scalaVersion.value, "atum-agent")
    ): _*
  )
  .jvmPlatform(scalaVersions = Versions.clientSupportedScalaVersions)
  .dependsOn(model)

lazy val model = (projectMatrix in file("model"))
  .settings(
    commonSettings ++ Seq(
      name := "model",
      libraryDependencies ++= Dependencies.modelDependencies(scalaVersion.value),
      printScalaVersion := {
        val log = streams.value.log
        log.info(s"Building ${name.value} with Scala ${scalaVersion.value}")
      },
      (Compile / compile) := ((Compile / compile) dependsOn printScalaVersion).value,
      jacocoReportSettings := jacocoSettings(scalaVersion.value, "atum-agent: model")
    ): _*
  )
  .jvmPlatform(scalaVersions = Versions.clientSupportedScalaVersions)

lazy val database = (projectMatrix in file("database"))
  .settings(
    commonSettings ++ Seq(
      name := "atum-database",
      printScalaVersion := {
        val log = streams.value.log
        log.info(s"Building ${name.value} with Scala ${scalaVersion.value}")
      },
      libraryDependencies ++= Dependencies.databaseDependencies,
      (Compile / compile) := ((Compile / compile) dependsOn printScalaVersion).value,
      test := {}
    ): _*
  )
  .jvmPlatform(scalaVersions = Seq(Versions.serviceScalaVersion))

lazy val dbTest = taskKey[Unit]("Launch DB tests")

dbTest := {
  println("Running DB tests")
  (database.jvm(Versions.serviceScalaVersion) / Test / test).value
}
