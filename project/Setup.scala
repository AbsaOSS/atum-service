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

import com.github.sbt.jacoco.JacocoKeys.jacocoExcludes
import sbt.*
import sbt.Keys.*
import sbtassembly.AssemblyKeys.assemblyMergeStrategy
import sbtassembly.AssemblyPlugin.autoImport.{MergeStrategy, assembly}
import sbtassembly.PathList
import za.co.absa.commons.version.Version


object Setup {
  //supported Java versions
  val requiredJava: Double = "1.8".toDouble
  val recommendedJava: Double = "11".toDouble
  val currentJava: Double =  sys.props("java.specification.version").toDouble

  //possible supported Scala versions
  val scala211: Version = Version.asSemVer("2.11.12")
  val scala212: Version = Version.asSemVer("2.12.18")
  val scala213: Version = Version.asSemVer("2.13.13")

  lazy val commonSettings: Seq[SettingsDefinition] = Seq(
    scalacOptions ++= Setup.commonScalacOptions,
    Test / parallelExecution := false,
    jacocoExcludes := JacocoSetup.jacocoProjectExcludes(),
    (assembly / test) := {},
    (publish / test) := { (Test / testOnly).toTask(" *UnitTests").value }
  )

  val serverAndDbScalaVersion: Version = scala213 //covers REST server and database modules
  val clientSupportedScalaVersions: Seq[Version] = Seq(
    scala212,
    scala213,
  )

  val commonScalacOptions: Seq[String] = Seq(
    "-unchecked",
    "-deprecation",
    "-feature",
    "-Xfatal-warnings"
  )

  val serverAndDbJavacOptions: Seq[String] = Seq(
    "-source", "11",
    "-target", "11",
    "-Xlint"
  )
  val serverAndDbScalacOptions: Seq[String] = Seq(
    "-language:higherKinds",
    "-Ymacro-annotations"
  )

  val clientJavacOptions: Seq[String] = Seq("-source", "1.8", "-target", "1.8", "-Xlint")
  def clientScalacOptions(scalaVersion: Version): Seq[String] = {
    if (scalaVersion >= scala213) {
      Seq(
        "-release", "8",
        "-language:higherKinds",
        "-Ymacro-annotations"
      )
    } else {
      Seq(
        "-release", "8",
        "-language:higherKinds",
        "-target:8"
      )
    }
  }

  val serverMergeStrategy = assembly / assemblyMergeStrategy := {
    case PathList("META-INF", "services", _*) => MergeStrategy.concat
    case PathList("META-INF", "maven", "org.webjars", "swagger-ui", "pom.properties") => MergeStrategy.singleOrError
    case PathList("META-INF", "resources", "webjars", "swagger-ui", _*) => MergeStrategy.singleOrError
    case PathList("META-INF", _*) => MergeStrategy.discard
    case PathList("META-INF", "versions", "9") => MergeStrategy.discard
    case PathList("module-info.class") => MergeStrategy.discard
    case "application.conf" => MergeStrategy.concat
    case "reference.conf" => MergeStrategy.concat
    case _ => MergeStrategy.first
  }
}
