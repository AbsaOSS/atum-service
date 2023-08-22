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

ThisBuild / organization := "za.co.absa.atum-service"

lazy val scala211 = "2.11.12"
lazy val scala212 = "2.12.12"
lazy val spark2 = "2.4.7"
lazy val spark3 = "3.3.1"

ThisBuild / crossScalaVersions := Seq(scala211, scala212)
ThisBuild / scalaVersion := scala212

ThisBuild / versionScheme := Some("early-semver")

Global / onChangedBuildSource := ReloadOnSourceChanges

lazy val printScalaVersion = taskKey[Unit]("Print Scala versions for atum-service is being built for.")

ThisBuild / printScalaVersion := {
  val log = streams.value.log
  log.info(s"Building with Scala ${scalaVersion.value}")
}

lazy val commonSettings = Seq(
  libraryDependencies ++= commonDependencies,
  scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Xfatal-warnings"),
  javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint"),
  Test / parallelExecution := false
)

lazy val parent = (project in file("."))
  .aggregate(atumServer.projectRefs ++ atumAgent.projectRefs: _*)
  .settings(
    name := "atum-service-parent",
    publish / skip := true
  )

lazy val atumAgent = (projectMatrix in file("agent"))
  .settings(
    commonSettings ++ Seq(
      name := "atum-agent",
      (Compile / compile) := ((Compile / compile) dependsOn printScalaVersion).value,
      scalafmtOnCompile := true
    )
  )
  .enablePlugins(ScalafmtPlugin)
  .sparkRow(SparkVersionAxis(spark2), scalaVersions = Seq(scala211, scala212))
  .sparkRow(SparkVersionAxis(spark3), scalaVersions = Seq(scala212))

lazy val atumServer = (projectMatrix in file("server"))
  .settings(
    commonSettings ++ Seq(
      name := "atum-server",
      libraryDependencies ++= Dependencies.serverDependencies,
      (Compile / compile) := ((Compile / compile) dependsOn printScalaVersion).value,
      packageBin := (assembly in Compile).value,
//      artifactPath in (Compile, packageBin) := baseDirectory.value / s"target/${name.value}-${version.value}.war",
      webappWebInfClasses := true,
      inheritJarManifest := true
    ): _*
  )
  .settings(
    assemblyOutputPath / assembly := baseDirectory.value / s"target/${name.value}-${version.value}.war",
    jacocoReportSettings := jacocoSettings( scalaVersion.value, "atum-server"),
    jacocoExcludes := jacocoProjectExcludes()
  )
  .enablePlugins(AssemblyPlugin)
  .enablePlugins(TomcatPlugin)
  .enablePlugins(AutomateHeaderPlugin)
  .jvmPlatform(scalaVersions = Seq(scala212))

