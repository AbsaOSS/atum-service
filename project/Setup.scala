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
import sbtassembly.AssemblyKeys.assemblyMergeStrategy
import sbtassembly.AssemblyPlugin.autoImport.{MergeStrategy, assembly}
import sbtassembly.PathList
import za.co.absa.commons.version.Version


object Setup {
  val currentJava: Double =  sys.props("java.specification.version").toDouble

  //supported Java versions
  val spark3RequiredJava: Double = "1.8".toDouble // absolute minimum
  val recommendedJava: Double = "11".toDouble

  //Spark 4 has a Java 17 baseline, so its build rows cannot even be compiled on an older JDK
  val spark4RequiredJava: Double = "17".toDouble
  val spark4Supported: Boolean = currentJava >= spark4RequiredJava

  //possible supported Scala versions
  val scala212: Version = Version.asSemVer("2.12.18")
  val scala213: Version = Version.asSemVer("2.13.18")

  lazy val commonSettings: Seq[SettingsDefinition] = Seq(
    scalacOptions ++= Setup.commonScalacOptions,
    Test / parallelExecution := false,
    (assembly / test) := {},
    (publish / test) := { (Test / testOnly).toTask(" *UnitTests").value }
  )

  val serverAndDbScalaVersion: Version = scala213 //covers REST server and database modules
  val clientSupportedScalaVersions: Seq[Version] = Seq(
    scala212,
    scala213,
  )

  /**
   * Scala versions supported for a given Spark axis. Spark 4 dropped Scala 2.12, so the 2.12 row must never be
   * attempted against it - resolution of `spark-core_2.12:4.x` would simply fail.
   */
  def clientSupportedScalaVersions(sparkVersion: String): Seq[Version] = {
    if (Dependencies.Versions.isSpark4OrLater(sparkVersion)) Seq(scala213)
    else clientSupportedScalaVersions
  }

  //Java bytecode level the client modules (agent, model, reader) are compiled down to
  val spark3ClientJavaTarget: String = "1.8"
  val spark4ClientJavaTarget: String = "17"

  /** Spark 3 artifacts stay on Java 8 bytecode; Spark 4 requires a Java 17 baseline. */
  def clientJavaTarget(sparkVersion: String): String = {
    if (Dependencies.Versions.isSpark4OrLater(sparkVersion)) spark4ClientJavaTarget
    else spark3ClientJavaTarget
  }

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

  def clientJavacOptions(javaTarget: String = spark3ClientJavaTarget): Seq[String] =
    Seq("-source", javaTarget, "-target", javaTarget, "-Xlint")

  def clientScalacOptions(scalaVersion: Version, javaTarget: String = spark3ClientJavaTarget): Seq[String] = {
    //scalac's `-release` takes the JEP-322 feature number, so "1.8" has to be normalised to "8"
    val release = javaTarget.stripPrefix("1.")
    if (scalaVersion >= scala213) {
      Seq(
        "-release", release,
        "-language:higherKinds",
        "-Ymacro-annotations"
      )
    } else {
      Seq(
        "-release", release,
        "-language:higherKinds",
        s"-target:$release"
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
