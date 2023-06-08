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

  //Todo this dependencies should be

  val serverDependencies: Seq[ModuleID] = {
    val springVersion = "2.6.1"
    val springOrg = "org.springframework.boot"

    lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.9"
    lazy val springBootWeb = springOrg % "spring-boot-starter-web" % springVersion
    lazy val springBootConfiguration = springOrg % "spring-boot-configuration-processor" % springVersion
    lazy val springBootTomcat = springOrg % "spring-boot-starter-tomcat" % springVersion
    lazy val springBootTest = springOrg % "spring-boot-starter-test" % springVersion
    lazy val servletApi = "javax.servlet" % "javax.servlet-api" % "3.0.1"
    lazy val springFoxSwagger = "io.springfox" % "springfox-swagger2" % "3.0.0"
    lazy val springFoxBoot = "io.springfox" % "springfox-boot-starter" % "3.0.0"
    lazy val springFoxSwaggerUI = "io.springfox" % "springfox-swagger-ui" % "3.0.0"

    // controller implicits:  java CompletableFuture -> scala Future
    lazy val scalaJava8Compat = "org.scala-lang.modules" %% "scala-java8-compat" % "1.0.2"
    // object mapper serialization
    lazy val jacksonModuleScala = "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.13.1"


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

}

object AgentDependencies {

  object Versions {
    val spark2 = "2.4.8"
    val spark3 = "3.2.2"

    val json4s_spark2 = "3.5.3"
    val json4s_spark3 = "3.7.0-M11"

    val jacksonModuleScala_spark2 = "2.12.7"
    val jacksonModuleScala_spark3 = "2.14.1"
    val jacksonDatabind_spark2 = "2.12.7.1" // databind has extra extra patch for this version - for Spark2
    val jacksonDatabind_spark3 = jacksonModuleScala_spark3 // for Spark3 - latest version is ok

    val absaCommons = "0.0.27"
    val typesafeConfig = "1.4.1"
    val mockitoScala = "1.17.12"
    val scalatest = "3.2.9"
    val specs2 = "2.5"
    val aws = "2.17.85"

    val apacheCommonsLang3 = "3.12.0"
    val commonsConfiguration = "1.6"
  }

  // basic idea of crossversion version picking is based on https://github.com/scala/scala-module-dependency-sample

  // this is just for the compile-depended printing task
  def sparkVersionForScala(scalaVersion: String): String = {
    scalaVersion match {
      case _ if scalaVersion.startsWith("2.11") => Versions.spark2
      case _ if scalaVersion.startsWith("2.12") => Versions.spark3
      case _ => throw new IllegalArgumentException("Only Scala 2.11 and 2.12 are currently supported.")
    }
  }

  // general wrapper to simplify s2.11/2.12 version assigning
  def moduleByScala(moduleIdWithoutVersion: String => ModuleID)
                   (scala211Version: String, scala212Version: String)
                   (actualScalaVersion: String): ModuleID = {
    actualScalaVersion match {
      case _ if actualScalaVersion.startsWith("2.11") => moduleIdWithoutVersion.apply(scala211Version)
      case _ if actualScalaVersion.startsWith("2.12") => moduleIdWithoutVersion.apply(scala212Version)
      case _ => throw new IllegalArgumentException("Only Scala 2.11 and 2.12 are currently supported.")
    }
  }


  // extended version where to moduleId Fn takes 2 params: module version and scala version (to pass along)
  def moduleByScalaUsingScalaVersion(moduleIdWithoutVersionNeedsScalaVersion: (String, String) => ModuleID)
                                    (scala211Version: String, scala212Version: String)
                                    (actualScalaVersion: String): ModuleID = {
    actualScalaVersion match {
      case _ if actualScalaVersion.startsWith("2.11") => moduleIdWithoutVersionNeedsScalaVersion.apply(scala211Version, actualScalaVersion)
      case _ if actualScalaVersion.startsWith("2.12") => moduleIdWithoutVersionNeedsScalaVersion.apply(scala212Version, actualScalaVersion)
      case _ => throw new IllegalArgumentException("Only Scala 2.11 and 2.12 are currently supported.")
    }
  }


  lazy val sparkCore: String => sbt.ModuleID = {
    def coreWithExcludes(version: String, scalaVersion: String): ModuleID = "org.apache.spark" %% "spark-core" % version /*% Provided*/ exclude(
      "com.fasterxml.jackson.core", "jackson-databind"
    ) exclude(
      "com.fasterxml.jackson.module", "jackson-module-scala_" + scalaVersion.substring(0, 4)  // e.g. 2.11
    )
    moduleByScalaUsingScalaVersion(coreWithExcludes)(Versions.spark2, Versions.spark3) _
  }

  lazy val sparkSql: String => sbt.ModuleID =
    moduleByScala("org.apache.spark" %% "spark-sql" % _ /*% Provided*/)(Versions.spark2, Versions.spark3)

  lazy val scalaTest = "org.scalatest" %% "scalatest" % Versions.scalatest % Test

  lazy val json4sExt = moduleByScala("org.json4s" %% "json4s-ext" % _)(Versions.json4s_spark2, Versions.json4s_spark3) _
  lazy val json4sCore = moduleByScala("org.json4s" %% "json4s-core" % _ /*% Provided*/)(Versions.json4s_spark2, Versions.json4s_spark3) _
  lazy val json4sJackson = moduleByScala("org.json4s" %% "json4s-jackson" % _ /*% Provided*/)(Versions.json4s_spark2, Versions.json4s_spark3) _
  lazy val json4sNative = moduleByScala("org.json4s" %% "json4s-native" % _ /*% Provided*/)(Versions.json4s_spark2, Versions.json4s_spark3) _

  lazy val jacksonModuleScala = moduleByScala("com.fasterxml.jackson.module" %% "jackson-module-scala" % _)(Versions.jacksonModuleScala_spark2, Versions.jacksonModuleScala_spark3) _
  lazy val jacksonDatabind = moduleByScala("com.fasterxml.jackson.core" % "jackson-databind" % _)(Versions.jacksonDatabind_spark2, Versions.jacksonDatabind_spark3) _

  lazy val absaCommons = "za.co.absa.commons" %% "commons" % Versions.absaCommons
  lazy val commonsConfiguration = "commons-configuration" % "commons-configuration" % Versions.commonsConfiguration
  lazy val apacheCommons = "org.apache.commons" % "commons-lang3" % Versions.apacheCommonsLang3
  lazy val typeSafeConfig = "com.typesafe" % "config" % Versions.typesafeConfig

  lazy val mockitoScala = "org.mockito" %% "mockito-scala" % Versions.mockitoScala % Test
  lazy val mockitoScalaScalatest = "org.mockito" %% "mockito-scala-scalatest" % Versions.mockitoScala % Test

  lazy val scalaTestProvided = "org.scalatest" %% "scalatest" % Versions.scalatest ///*% Provided*/
  lazy val specs2core = "org.specs2" %% "specs2-core" % Versions.specs2 % Test

  lazy val sdkS3 = "software.amazon.awssdk" % "s3" % Versions.aws

  def rootDependencies(scalaVersion: String): Seq[ModuleID] = Seq(
    sparkCore(scalaVersion),
    sparkSql(scalaVersion),
    scalaTest,
    json4sExt(scalaVersion)
  )

  def modelDependencies(scalaVersion: String): Seq[ModuleID] = Seq(
    json4sCore(scalaVersion),
    json4sJackson(scalaVersion),
    json4sNative(scalaVersion),
    jacksonModuleScala(scalaVersion),
    jacksonDatabind(scalaVersion)
  )

  def coreDependencies(scalaVersion: String): Seq[ModuleID] = Seq(
    absaCommons,
    commonsConfiguration,
    apacheCommons,
    typeSafeConfig,
    mockitoScala,
    mockitoScalaScalatest,
  )

  lazy val examplesDependencies: Seq[ModuleID] = Seq(
    specs2core,
    scalaTestProvided
  )

  lazy val s3sdkExtensionDependencies: Seq[ModuleID] = Seq(
    absaCommons,
    sdkS3,
    mockitoScala,
    mockitoScalaScalatest
  )

}


