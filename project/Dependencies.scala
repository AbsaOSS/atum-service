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
  object Versions { // focusing on versions that are user >1x
    val springVersion = "2.6.1"
    val mockitoScala = "1.15.0"
    val swagger = "3.0.0"
  }

  val springOrg = "org.springframework.boot"

  lazy val springBootWeb = springOrg % "spring-boot-starter-web" % Versions.springVersion
  lazy val springBootConfiguration = springOrg % "spring-boot-configuration-processor" % Versions.springVersion
  lazy val springBootTomcat = springOrg % "spring-boot-starter-tomcat" % Versions.springVersion
  lazy val servletApi = "javax.servlet" % "javax.servlet-api" % "3.0.1"
  lazy val springFoxSwagger = "io.springfox" % "springfox-swagger2" % Versions.swagger
  lazy val springFoxBoot = "io.springfox" % "springfox-boot-starter" % Versions.swagger
  lazy val springFoxSwaggerUI = "io.springfox" % "springfox-swagger-ui" % Versions.swagger

  // controller implicits:  java CompletableFuture -> scala Future
  lazy val scalaJava8Compat = "org.scala-lang.modules" %% "scala-java8-compat" % "1.0.2"
  // object mapper serialization
  lazy val jacksonModuleScala = "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.13.1"

  // test support
  lazy val springBootTest = springOrg % "spring-boot-starter-test" % Versions.springVersion
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.9"
  lazy val mockitoScala = "org.mockito" %% "mockito-scala" % Versions.mockitoScala
  lazy val mockitoScalaScalatest = "org.mockito" %% "mockito-scala-scalatest" % Versions.mockitoScala


  lazy val dependencies: Seq[ModuleID] = Seq(
    springBootWeb,
    springBootConfiguration,
    springBootTomcat % Provided,
    servletApi % Provided,
    springFoxSwagger,
    springFoxSwaggerUI,
    springFoxBoot,
    scalaJava8Compat,
    jacksonModuleScala,

    scalaTest % Test,
    springBootTest % Test,
    mockitoScala % Test,
    mockitoScalaScalatest % Test

  )
}
