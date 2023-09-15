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
    "org.scalatest" %% "scalatest" % "3.2.2" % Test,
    "org.mockito" %% "mockito-scala" % "1.17.12" % Test
  )

  def serverDependencies: Seq[ModuleID] = {

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
      springBootTomcat /*% Provided*/ ,
      servletApi /*% Provided*/ ,
      springFoxSwagger,
      springFoxSwaggerUI,
      springFoxBoot,
      scalaJava8Compat,
      jacksonModuleScala
    )
  }

  def agentDependencies(sparkVersion: String): Seq[ModuleID] = {

    val typesafeVersion = "1.4.2"
    val sparkCommonsVersion = "0.6.0"
    val sparkMinorVersion = getVersionUpToMinor(sparkVersion)
    val specs2CoreVersion = "4.19.2"

    Seq(
      "org.apache.spark" %% "spark-core" % sparkVersion % Provided,
      "org.apache.spark" %% "spark-sql" % sparkVersion % Provided,
      "com.typesafe" % "config" % typesafeVersion,
      "za.co.absa" %% s"spark-commons-spark${sparkMinorVersion}" % sparkCommonsVersion,
      "com.softwaremill.sttp.client3" %% "core" % "3.5.2",
      "za.co.absa" %% "spark-commons-test" % sparkCommonsVersion % Test
    )
  }

}
