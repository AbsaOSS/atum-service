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

import sbt.*
import za.co.absa.commons.version.{Component, Version}

object Dependencies {

  object Versions {
    val spark2 = "2.4.7"
    val spark3 = "3.3.2"

    val scalatest = "3.2.15"
    val scalaMockito = "1.17.12"
    val scalaLangJava8Compat = "1.0.2"
    val balta = "0.1.0"

    val specs2 = "4.10.0"
    val typesafeConfig = "1.4.2"

    val spring = "2.6.15"

    val javaxServlet = "4.0.1"

    val springfox = "3.0.0"

    val sparkCommons = "0.6.1"

    val sttpClient = "3.5.2"
    val sttpCirceJson = "3.9.7"

    val postgresql = "42.6.0"

    val fadb = "0.5.0"

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
    val circeJson = "0.14.7"

    val awssdk = "2.23.15"

    val scalaNameof = "4.0.0"

    val absaCommons = "2.0.0"

    def truncateVersion(version: String, parts: Int): String = {
      version.split("\\.").take(parts).mkString(".")
    }

    def getVersionUpToMinor(version: String): String = {
      truncateVersion(version, 2)
    }

    def getVersionUpToMajor(version: String): String = {
      truncateVersion(version, 1)
    }
  }

  private def testDependencies: Seq[ModuleID] = {
    lazy val scalatest = "org.scalatest" %% "scalatest" % Versions.scalatest % Test
    lazy val mockito = "org.mockito" %% "mockito-scala" % Versions.scalaMockito % Test

    Seq(
      scalatest,
      mockito
    )
  }

  private def jsonSerdeDependencies: Seq[ModuleID] = {

    // Circe dependencies
    lazy val circeCore = "io.circe" %% "circe-core" % Versions.circeJson
    lazy val circeParser = "io.circe" %% "circe-parser" % Versions.circeJson
    lazy val circeGeneric = "io.circe" %% "circe-generic" % Versions.circeJson

    Seq(
      circeCore,
      circeParser,
      circeGeneric
    )
  }

  def serverDependencies: Seq[ModuleID] = {
    val zioOrg = "dev.zio"
    val tapirOrg = "com.softwaremill.sttp.tapir"
    val http4sOrg = "org.http4s"
    val faDbOrg = "za.co.absa.db.fa-db"
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

    lazy val tapirCirce = tapirOrg %% "tapir-json-circe" % Versions.tapir
    lazy val tapirOpenApiDocs = tapirOrg %% "tapir-openapi-docs" % Versions.tapir
    lazy val tapirOpenApiCirceYaml = tapirOrg %% "tapir-openapi-circe-yaml" % Versions.tapir
    lazy val tapirHttp4sServer = tapirOrg %% "tapir-http4s-server" % Versions.tapir
    lazy val tapirCore = tapirOrg %% "tapir-core" % Versions.tapir
    lazy val tapirSwaggerUi = tapirOrg %% "tapir-swagger-ui-http4s" % Versions.tapir

    // STTP core and Circe integration
    lazy val sttpCirce = sttpClient3Org %% "circe" % Versions.sttpCirceJson % Test
    lazy val sttpCore = sttpClient3Org %% "core" % Versions.sttpCirceJson
    lazy val clientBackend = sttpClient3Org %% "async-http-client-backend-zio" % Versions.sttpCirceJson

    // Fa-db
    lazy val faDbDoobie = faDbOrg %% "doobie" % Versions.fadb

    // aws
    lazy val awsSecretsManagerSdk = awsSdkOrg % "secretsmanager" % Versions.awssdk
    lazy val awsStsSdk = awsSdkOrg % "sts" % Versions.awssdk

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
      tapirCirce,
      tapirPrometheus,
      tapirStubServer,
      sttpCirce,
      sttpCore,
      awsSecretsManagerSdk,
      awsStsSdk,
      zioTest,
      zioTestSbt,
      zioTestJunit,
      sbtJunitInterface
    ) ++
      testDependencies
  }

  def agentDependencies(sparkVersion: String, scalaVersion: Version): Seq[ModuleID] = {
    val sparkMinorVersion = Versions.getVersionUpToMinor(sparkVersion)
    val scalaMinorVersion = Versions.getVersionUpToMinor(scalaVersion.asString)

    lazy val sparkCore = "org.apache.spark" %% "spark-core" % sparkVersion % Provided
    lazy val sparkSql = "org.apache.spark" %% "spark-sql" % sparkVersion % Provided
    lazy val typeSafeConfig = "com.typesafe" % "config" % Versions.typesafeConfig

    lazy val sparkCommons = "za.co.absa" % s"spark-commons-spark${sparkMinorVersion}_$scalaMinorVersion" % Versions.sparkCommons
    lazy val sparkCommonsTest = "za.co.absa" % s"spark-commons-test_$scalaMinorVersion" % Versions.sparkCommons % Test

    lazy val sttpClient3 = "com.softwaremill.sttp.client3" %% "core" % Versions.sttpClient
    lazy val sttpOkHttpBackend = "com.softwaremill.sttp.client3" %% "okhttp-backend" % Versions.sttpClient

    lazy val logback = "ch.qos.logback" % "logback-classic" % Versions.logback

    lazy val nameOf = "com.github.dwickern" %% "scala-nameof" % Versions.scalaNameof % Provided // it's provided, as it's a macro needed only at runtime

    lazy val balta = "za.co.absa" %% "balta" % Versions.balta % Test

    Seq(
      sparkCore,
      sparkSql,
      typeSafeConfig,
      sparkCommons,
      sparkCommonsTest,
      sttpClient3,
      sttpOkHttpBackend,
      logback,
      nameOf
    ) ++
      testDependencies :+ balta
  }

  def modelDependencies(scalaVersion: Version): Seq[ModuleID] = {
    lazy val specs2core =     "org.specs2"      %% "specs2-core"  % Versions.specs2 % Test
    lazy val typeSafeConfig = "com.typesafe"     % "config"       % Versions.typesafeConfig

    Seq(
      specs2core,
      typeSafeConfig
    ) ++
      testDependencies ++
      jsonSerdeDependencies
  }

  def readerDependencies(scalaVersion: Version): Seq[ModuleID] = {
    Seq(
    ) ++
      testDependencies
  }

  def databaseDependencies: Seq[ModuleID] = {
    lazy val scalaTest  = "org.scalatest"   %% "scalatest"    % Versions.scalatest  % Test
    lazy val balta      = "za.co.absa"      %% "balta"        % Versions.balta      % Test
    lazy val circe      = "io.circe"        %% "circe-core"   % Versions.circeJson  % Test
    lazy val parser     = "io.circe"        %% "circe-parser" % Versions.circeJson  % Test

    Seq(
      scalaTest,
      balta,
      circe,
      parser
    )
  }

  def flywayDependencies: Seq[ModuleID] = {
    val postgresql = "org.postgresql" % "postgresql" % Versions.postgresql

    Seq(postgresql)
  }

}
