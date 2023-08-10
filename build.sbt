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

import Dependencies._

ThisBuild / organization := "za.co.absa"

enablePlugins(JettyPlugin)

lazy val scala211 = "2.11.12"
lazy val scala212 = "2.12.12"

Test / parallelExecution := false

Global / onChangedBuildSource := ReloadOnSourceChanges

lazy val root = (project in file("."))
  .settings(
    name := "atum-root",
    javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint")
  )

lazy val server = project
  .settings(
    name         := "atum-server",
    scalaVersion := scala212,
    libraryDependencies ++= Dependencies.serverDependencies,
    webappWebInfClasses := true,
    inheritJarManifest  := true,
    Compile / packageBin / publishArtifact := false, // disable .jar publishing
    // create an Artifact for publishing the .war file
//    Compile / packageWar / artifact := {
//      val prev: Artifact = (Compile / packageWar / artifact).value
//      prev.withType("war").withExtension("war")
//    },
//
//    // add the .war file to what gets published
//    addArtifact(Compile / packageWar / artifact, packageWar)
  )
  .enablePlugins(JettyPlugin)
  .enablePlugins(TomcatPlugin)
  .enablePlugins(AutomateHeaderPlugin)

lazy val agent = project
  .settings(
    name         := "atum-agent",
    scalaVersion := scala212,
    libraryDependencies ++= Dependencies.agentDependencies,
    scalafmtOnCompile := true
  ).enablePlugins(ScalafmtPlugin)
