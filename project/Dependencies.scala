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
    val balta = "0.1.0"

    val jacksonModuleScala = "2.14.2"

    val specs2 = "4.10.0"
    val typesafeConfig = "1.4.2"

    val spring = "2.6.15"

    val javaxServlet = "4.0.1"

    val springfox = "3.0.0"

    val sparkCommons = "0.6.1"

    val sttp = "3.5.2"

    val postgresql = "42.6.0"

    val fadb = "0.3.0"

    val json4s_spark2 = "3.5.3"
    val json4s_spark3 = "3.7.0-M11"

    val logback = "1.2.3"

    val zio = "2.0.19"
    val zioLogging = "2.2.0"
    val logbackZio = "1.4.7"
    val zioConfig = "4.0.1"
    val zioMetricsConnectors = "2.3.1"
    val sbtJunitInterface = "0.13.3"
    val tapir = "1.9.6"
    val http4sBlazeBackend = "0.23.15"
    val http4sPrometheus = "0.23.6"
    val playJson = "3.0.1"
    val sttpPlayJson = "3.9.3"

    val awssdk = "2.23.15"
  }


  private def truncateVersion(version: String, parts: Int): String = {
    version.split("\\.").take(parts).mkString(".")
  }

  def getVersionUpToMinor(version: String): String = {
    truncateVersion(version, 2)
  }

  def getVersionUpToMajor(version: String): String = {
    truncateVersion(version, 1)
  }

  // this is just for the compile-depended printing task
  def sparkVersionForScala(scalaVersion: String): String = {
    truncateVersion(scalaVersion, 2) match {
      case "2.11" => Versions.spark2
      case "2.12" => Versions.spark3
      case "2.13" => Versions.spark3
      case _ => throw new IllegalArgumentException("Only Scala 2.11, 2.12, and 2.13 are currently supported.")
    }
  }

  def json4sVersionForScala(scalaVersion: String): String = {
    truncateVersion(scalaVersion, 2) match {
      case "2.11" => Versions.json4s_spark2
      case "2.12" => Versions.json4s_spark3
      case "2.13" => Versions.json4s_spark3
      case _ => throw new IllegalArgumentException("Only Scala 2.11, 2.12, and 2.13 are currently supported.")
    }
  }

  def testDependencies: Seq[ModuleID] = {
    lazy val scalatest = "org.scalatest" %% "scalatest" % Versions.scalatest % Test
    lazy val mockito = "org.mockito" %% "mockito-scala" % Versions.scalaMockito % Test

    Seq(
      scalatest,
      mockito,
    )
  }

  def jsonSerdeDependencies: Seq[ModuleID] = {
    val json4sVersion = json4sVersionForScala(Versions.scala212)

    lazy val jacksonModuleScala = "com.fasterxml.jackson.module" %% "jackson-module-scala" % Versions.jacksonModuleScala

    lazy val json4sExt = "org.json4s" %% "json4s-ext" % json4sVersion
    lazy val json4sCore = "org.json4s" %% "json4s-core" % json4sVersion
    lazy val json4sJackson = "org.json4s" %% "json4s-jackson" % json4sVersion
    lazy val json4sNative = "org.json4s" %% "json4s-native" % json4sVersion % Provided

    Seq(
      jacksonModuleScala,
      json4sExt,
      json4sCore,
      json4sJackson,
      json4sNative
    )
  }

  def serverDependencies: Seq[ModuleID] = {
    val zioOrg = "dev.zio"
    val tapirOrg = "com.softwaremill.sttp.tapir"
    val http4sOrg = "org.http4s"
    val faDbOrg = "za.co.absa.fa-db"
    val playOrg = "org.playframework"
    val sbtOrg = "com.github.sbt"
    val logbackOrg = "ch.qos.logback"
    val awsSdkOrg = "software.amazon.awssdk"
    val sttpClient3Org = "com.softwaremill.sttp.client3"

    // zio
    lazy val zioCore = zioOrg %% "zio" % Versions.zio
    lazy val zioMacros = zioOrg %% "zio-macros" % Versions.zio
    lazy val zioLogging = zioOrg %% "zio-logging" % Versions.zioLogging
    lazy val slf4jLogging = zioOrg %% "zio-logging-slf4j2" % Versions.zioLogging
    lazy val logback = logbackOrg % "logback-classic" % Versions.logbackZio
    lazy val zioConfig = zioOrg %% "zio-config" % Versions.zioConfig
    lazy val zioConfigMagnolia = zioOrg %% "zio-config-magnolia" % Versions.zioConfig
    lazy val zioConfigTypesafe = zioOrg %% "zio-config-typesafe" % Versions.zioConfig
    lazy val zioMetricsConnectors = zioOrg %% "zio-metrics-connectors-prometheus" % Versions.zioMetricsConnectors

    // http4s
    lazy val http4sBlazeBackend = http4sOrg %% "http4s-blaze-server" % Versions.http4sBlazeBackend
    lazy val http4sPrometheus = http4sOrg %% "http4s-prometheus-metrics" % Versions.http4sPrometheus

    // tapir
    lazy val tapirHttp4sZio = tapirOrg %% "tapir-http4s-server-zio" % Versions.tapir
    lazy val tapirSwagger = tapirOrg %% "tapir-swagger-ui-bundle" % Versions.tapir
    lazy val tapirPlayJson = tapirOrg %% "tapir-json-play" % Versions.tapir
    lazy val tapirPrometheus = tapirOrg %% "tapir-prometheus-metrics" % Versions.tapir
    lazy val tapirStubServer = tapirOrg %% "tapir-sttp-stub-server" % Versions.tapir % Test

    // json
    lazy val playJson = playOrg %% "play-json" % Versions.playJson
    lazy val sttpPlayJson = sttpClient3Org %% "play-json" % Versions.sttpPlayJson % Test

    // Fa-db
    lazy val faDbDoobie = faDbOrg %% "doobie" % Versions.fadb

    // aws
    lazy val awsSecretsManagerSdk = awsSdkOrg % "secretsmanager" % Versions.awssdk

    // testing
    lazy val zioTest = zioOrg %% "zio-test" % Versions.zio % Test
    lazy val zioTestSbt = zioOrg %% "zio-test-sbt" % Versions.zio % Test
    lazy val zioTestJunit = zioOrg %% "zio-test-junit" % Versions.zio % Test
    lazy val sbtJunitInterface = sbtOrg % "junit-interface" % Versions.sbtJunitInterface % Test

    Seq(
      faDbDoobie,
      zioCore,
      zioMacros,
      zioLogging,
      slf4jLogging,
      logback,
      zioConfig,
      zioConfigMagnolia,
      zioConfigTypesafe,
      zioMetricsConnectors,
      http4sBlazeBackend,
      http4sPrometheus,
      tapirHttp4sZio,
      tapirSwagger,
      tapirPlayJson,
      tapirPrometheus,
      tapirStubServer,
      playJson,
      sttpPlayJson,
      awsSecretsManagerSdk,
      zioTest,
      zioTestSbt,
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

    lazy val logback = "ch.qos.logback" % "logback-classic" % Versions.logback

    Seq(
      sparkCore,
      sparkSql,
      typeSafeConfig,
      sparkCommons,
      sparkCommonsTest,
      sttp,
      logback
    )
  }

  def modelDependencies(scalaVersion: String): Seq[ModuleID] = {
    lazy val specs2core =     "org.specs2"      %% "specs2-core"  % Versions.specs2 % Test
    lazy val typeSafeConfig = "com.typesafe"     % "config"       % Versions.typesafeConfig

    Seq(specs2core, typeSafeConfig)
  }

 def databaseDependencies: Seq[ModuleID] = {
    lazy val scalaTest  = "org.scalatest"   %% "scalatest"  % Versions.scalatest  % Test
    lazy val balta =      "za.co.absa"      %% "balta"      % Versions.balta      % Test

    Seq(
      scalaTest,
      balta,
    )
  }

  def flywayDependencies: Seq[ModuleID] = {
    val postgresql = "org.postgresql" % "postgresql" % Versions.postgresql

    Seq(postgresql)
  }

}
