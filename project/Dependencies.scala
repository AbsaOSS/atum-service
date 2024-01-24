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


import sbt._

object Dependencies {

  object Versions {
    val spark2 = "2.4.8"
    val spark3 = "3.3.2"

    val scala211 = "2.11.12"
    val scala212 = "2.12.18"
    val scala213 = "2.13.11"

    val serviceScalaVersion: String = scala213
    val clientSupportedScalaVersions: Seq[String] = Seq(scala211, scala212, scala213)

    val scalatest = "3.2.15"
    val scalaMockito = "1.17.12"
    val scalaLangJava8Compat = "1.0.2"

    val jacksonModuleScala = "2.14.2"

    val specs2 = "4.10.0"
    val typesafeConfig = "1.4.2"

    val spring = "2.6.15"

    val javaxServlet = "4.0.1"

    val springfox = "3.0.0"

    val sparkCommons = "0.6.1"

    val sttp = "3.5.2"

    val fadb = "0.3.0"

    val json4s_spark2 = "3.5.3"
    val json4s_spark3 = "3.7.0-M11"

    val logback = "1.2.3"

    val zio = "2.0.19"
    val zioLogging = "2.2.0"
    val zioConfig = "4.0.1"
    val tapir = "1.9.6"
    val http4sBlazeBackend = "0.23.15"
    val playJson = "2.9.4"
  }

  private def limitVersion(version: String, parts: Int): String = {
    version.split("\\.", parts + 1).take(parts).mkString(".")
  }

  def getVersionUpToMinor(version: String): String = {
    limitVersion(version, 2)
  }

  def getVersionUpToMajor(version: String): String = {
    limitVersion(version, 1)
  }

  // this is just for the compile-depended printing task
  def sparkVersionForScala(scalaVersion: String): String = {
    scalaVersion match {
      case _ if scalaVersion.startsWith("2.11") => Versions.spark2
      case _ if scalaVersion.startsWith("2.12") => Versions.spark3
      case _ if scalaVersion.startsWith("2.13") => Versions.spark3
      case _ => throw new IllegalArgumentException("Only Scala 2.11, 2.12, and 2.13 are currently supported.")
    }
  }

  def json4sVersionForScala(scalaVersion: String): String = {
    scalaVersion match {
      case _ if scalaVersion.startsWith("2.11") => Versions.json4s_spark2
      case _ if scalaVersion.startsWith("2.12") => Versions.json4s_spark3
      case _ if scalaVersion.startsWith("2.13") => Versions.json4s_spark3
      case _ => throw new IllegalArgumentException("Only Scala 2.11, 2.12, and 2.13 are currently supported.")
    }
  }

  def commonDependencies: Seq[ModuleID] = {
    val json4sVersion = json4sVersionForScala(Versions.scala212)

    lazy val jacksonModuleScala = "com.fasterxml.jackson.module" %% "jackson-module-scala" % Versions.jacksonModuleScala

    lazy val json4sExt = "org.json4s" %% "json4s-ext" % json4sVersion
    lazy val json4sCore = "org.json4s" %% "json4s-core" % json4sVersion
    lazy val json4sJackson = "org.json4s" %% "json4s-jackson" % json4sVersion
    lazy val json4sNative = "org.json4s" %% "json4s-native" % json4sVersion % Provided

    lazy val logback = "ch.qos.logback" % "logback-classic" % Versions.logback
    lazy val scalatest = "org.scalatest" %% "scalatest" % Versions.scalatest % Test
    lazy val mockito = "org.mockito" %% "mockito-scala" % Versions.scalaMockito % Test

    Seq(
      jacksonModuleScala,
      json4sExt,
      json4sCore,
      json4sJackson,
      json4sNative,
      logback,
      scalatest,
      mockito,
    )
  }

  def serverDependencies: Seq[ModuleID] = {
    val zioOrg = "dev.zio"
    val tapirOrg = "com.softwaremill.sttp.tapir"
    val http4sOrg = "org.http4s"
    val faDbOrg = "za.co.absa.fa-db"

    // zio
    lazy val zioCore = zioOrg %% "zio" % Versions.zio
    lazy val zioMacros = zioOrg %% "zio-macros" % Versions.zio
    lazy val zioLogging = zioOrg %% "zio-logging" % Versions.zioLogging
    lazy val zioConfig = zioOrg %% "zio-config" % Versions.zioConfig
    lazy val zioConfigMagnolia = zioOrg %% "zio-config-magnolia" % Versions.zioConfig
    lazy val zioConfigTypesafe = zioOrg %% "zio-config-typesafe" % Versions.zioConfig

    // http4s
    lazy val http4sBlazeBackend = http4sOrg %% "http4s-blaze-server" % Versions.http4sBlazeBackend

    // tapir
    lazy val tapirHttp4sZio = tapirOrg %% "tapir-http4s-server-zio" % Versions.tapir
    lazy val tapirSwagger = tapirOrg %% "tapir-swagger-ui-bundle" % Versions.tapir
    lazy val tapirPlayJson = tapirOrg %% "tapir-json-play" % Versions.tapir

    // json
    lazy val playJson = "com.typesafe.play" %% "play-json" % Versions.playJson

    // Fa-db
    lazy val faDbDoobie = faDbOrg %% "doobie" % Versions.fadb

    // testing
    lazy val zioTest = zioOrg %% "zio-test" % Versions.zio % Test
    lazy val zioTestSbt = zioOrg %% "zio-test-sbt" % Versions.zio % Test
//    lazy val zioTestMagnolia = zioOrg %% "zio-test-magnolia" % Versions.zio % Test
    lazy val zioTestJunit = zioOrg %% "zio-test-junit" % Versions.zio % Test
    lazy val sbtJunitInterface = "com.github.sbt" % "junit-interface" % "0.13.3" % Test

    Seq(
      faDbDoobie,
      zioCore,
      zioMacros,
      zioLogging,
      zioConfig,
      zioConfigMagnolia,
      zioConfigTypesafe,
      http4sBlazeBackend,
      tapirHttp4sZio,
      tapirSwagger,
      tapirPlayJson,
      playJson,
      zioTest,
      zioTestSbt,
//      zioTestMagnolia,
      zioTestJunit,
      sbtJunitInterface
    )
  }

  def agentDependencies(sparkVersion: String, scalaVersion: String): Seq[ModuleID] = {
    val sparkMinorVersion = getVersionUpToMinor(sparkVersion)
    val scalaMinorVersion = getVersionUpToMinor(scalaVersion)

    lazy val sparkCore = "org.apache.spark" %% "spark-core" % sparkVersion % Provided
    lazy val sparkSql = "org.apache.spark" %% "spark-sql" % sparkVersion % Provided
    lazy val typeSafeConfig = "com.typesafe" % "config" % Versions.typesafeConfig

    lazy val sparkCommons = "za.co.absa" % s"spark-commons-spark${sparkMinorVersion}_$scalaMinorVersion" % Versions.sparkCommons
    lazy val sparkCommonsTest = "za.co.absa" % s"spark-commons-test_$scalaMinorVersion" % Versions.sparkCommons % Test

    lazy val sttp = "com.softwaremill.sttp.client3" %% "core" % Versions.sttp

    Seq(
      sparkCore,
      sparkSql,
      typeSafeConfig,
      sparkCommons,
      sparkCommonsTest,
      sttp
    )
  }

  def modelDependencies(scalaVersion: String): Seq[ModuleID] = {
    val json4sVersion = json4sVersionForScala(scalaVersion)

    lazy val specs2core =     "org.specs2"      %% "specs2-core"  % Versions.specs2 % Test
    lazy val typeSafeConfig = "com.typesafe"     % "config"       % Versions.typesafeConfig

    Seq(specs2core, typeSafeConfig)
  }

}
