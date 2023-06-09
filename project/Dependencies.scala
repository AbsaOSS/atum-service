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

object Dependencies {

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


  val agentDependencies: Seq[ModuleID] = {

    val spark3Version = "3.2.2"
    val scala212 = "2.12.12"
    val scalatestVersion = "3.2.15"
    val specs2Version = "4.19.2"
    val typesafeConfigVersion = "1.4.2"

    lazy val sparkCore = "org.apache.spark" %% "spark-core" % spark3Version /*% Provided*/ /*exclude(
        "com.fasterxml.jackson.core", "jackson-databind"
      ) exclude(
        "com.fasterxml.jackson.module", "jackson-module-scala_" + scala212.substring(0, 4) // e.g. 2.11
      )*/

    lazy val sparkSql ="org.apache.spark" %% "spark-sql" %  spark3Version
    lazy val scalaTest = "org.scalatest" %% "scalatest" % scalatestVersion % Test
    lazy val specs2core = "org.specs2" %% "specs2-core" % specs2Version % Test
    lazy val typeSafeConfig = "com.typesafe" % "config" % typesafeConfigVersion

    Seq(sparkCore, sparkSql, scalaTest, specs2core, typeSafeConfig)

  }}



