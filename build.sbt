
import Dependencies._
import SparkVersionAxis._

ThisBuild / organization := "za.co.absa"

lazy val scala211 = "2.11.12"
lazy val scala212 = "2.12.12"
lazy val spark2 = "2.4.7"
lazy val spark3 = "3.3.1"

ThisBuild / crossScalaVersions := Seq(scala211, scala212)
ThisBuild / scalaVersion := scala212

Global / onChangedBuildSource := ReloadOnSourceChanges

lazy val commonSettings = Seq(
  libraryDependencies ++= commonDependencies,
  scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Xfatal-warnings"),
  javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint"),
  Test / parallelExecution := false
)

lazy val parent = (project in file("."))
  .aggregate(atumServer.projectRefs ++ atumAgent.projectRefs: _*)
  .settings(
    name := "atum-service-parent",
    publish / skip := true
  )

lazy val atumAgent = (projectMatrix in file("agent"))
  .settings(
    commonSettings ++ Seq(
      name := "atum-agent",
      scalafmtOnCompile := true
    )
  )
  .enablePlugins(ScalafmtPlugin)
  .sparkRow(SparkVersionAxis(spark2), scalaVersions = Seq(scala211, scala212))
  .sparkRow(SparkVersionAxis(spark3), scalaVersions = Seq(scala212))

lazy val atumServer = (projectMatrix in file("server"))
  .settings(
    commonSettings ++ Seq(
      name := "atum-server",
      libraryDependencies ++= Dependencies.serverDependencies,
      webappWebInfClasses := true,
      inheritJarManifest := true
    ): _*
  )
  .enablePlugins(TomcatPlugin)
  .enablePlugins(AutomateHeaderPlugin)
  .jvmPlatform(scalaVersions = Seq(scala212))

//lazy val projectMatrix = sparkRow(
//  sparkAxis = SparkVersionAxis(spark3),
//  scalaVersions = Seq(scala212, scala211),
//  modules = Seq(atumServer, atumAgent)
//)


