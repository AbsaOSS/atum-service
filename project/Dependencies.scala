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
    val spark3 = "3.3.2"

    val scala212 = "2.12.12"
    val scalatest = "3.2.15"
    val scalaMockito = "1.17.12"
    val scalaLangJava8Compat = "1.0.2"

    val jacksonModuleScala = "2.13.1"

    val specs2 = "4.19.2"
    val typesafeConfig = "1.4.2"

    val spring = "2.6.1"

    val javaxServlet = "3.0.1"
    val springfox = "3.0.0"

    val sparkCommons = "0.6.0"
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

  def commonDependencies: Seq[ModuleID] = Seq(
    "org.scalatest" %% "scalatest" % Versions.scalatest % Test,
    "org.mockito" %% "mockito-scala" % Versions.scalaMockito % Test
  )

  def serverDependencies: Seq[ModuleID] = {

    val springVersion = "2.6.1"
    val springOrg = "org.springframework.boot"

    lazy val scalaTest = "org.scalatest" %% "scalatest" % Versions.scalatest
    lazy val springBootWeb = springOrg % "spring-boot-starter-web" % Versions.spring
    lazy val springBootConfiguration = springOrg % "spring-boot-configuration-processor" % Versions.spring
    lazy val springBootTomcat = springOrg % "spring-boot-starter-tomcat" % Versions.spring
    lazy val springBootTest = springOrg % "spring-boot-starter-test" % Versions.spring
    lazy val servletApi = "javax.servlet" % "javax.servlet-api" % Versions.javaxServlet
    lazy val springFoxSwagger = "io.springfox" % "springfox-swagger2" % Versions.springfox
    lazy val springFoxBoot = "io.springfox" % "springfox-boot-starter" % Versions.springfox
    lazy val springFoxSwaggerUI = "io.springfox" % "springfox-swagger-ui" % Versions.springfox

    // controller implicits:  java CompletableFuture -> scala Future
    lazy val scalaJava8Compat = "org.scala-lang.modules" %% "scala-java8-compat" % Versions.scalaLangJava8Compat
    // object mapper serialization
    lazy val jacksonModuleScala = "com.fasterxml.jackson.module" %% "jackson-module-scala" % Versions.jacksonModuleScala

    Seq(
      scalaTest % Test,
      springBootTest % Test,
      springBootWeb,
      springBootConfiguration,
      springBootTomcat /*% Provided*/,
      servletApi /*% Provided*/,
      springFoxSwagger,
      springFoxSwaggerUI,
      springFoxBoot,
      scalaJava8Compat,
      jacksonModuleScala
    )
  }

  def agentDependencies(sparkVersion: String): Seq[ModuleID] = {

    val sparkMinorVersion   = getVersionUpToMinor(sparkVersion)

    lazy val sparkCore = "org.apache.spark" %% "spark-core" % Versions.spark3 % Provided
    lazy val sparkSql = "org.apache.spark" %% "spark-sql" % Versions.spark3 % Provided
    lazy val typeSafeConfig = "com.typesafe" % "config" % Versions.typesafeConfig

    lazy val sparkCommons = "za.co.absa" % s"spark-commons-spark${sparkMinorVersion}" % Versions.sparkCommons
    lazy val sparkCommonsTest = "za.co.absa" % "spark-commons-test" % Versions.sparkCommons % Test

    Seq(
      sparkCore,
      sparkSql,
      typeSafeConfig,
      sparkCommons,
      sparkCommonsTest
    )
  }

  def modelDependencies: Seq[ModuleID] = {
    lazy val scalaTest =      "org.scalatest"   %% "scalatest"    % Versions.scalatest % Test
    lazy val specs2core =     "org.specs2"      %% "specs2-core"  % Versions.specs2 % Test
    lazy val typeSafeConfig = "com.typesafe"     % "config"       % Versions.typesafeConfig

    lazy val jacksonModuleScala = "com.fasterxml.jackson.module" %% "jackson-module-scala" % Versions.jacksonModuleScala

    Seq(scalaTest, specs2core, typeSafeConfig, jacksonModuleScala)
  }

}
