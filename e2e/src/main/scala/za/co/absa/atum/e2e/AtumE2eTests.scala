/*
 * Copyright 2023 ABSA Group Limited
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

package za.co.absa.atum.e2e

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.spark.internal.Logging
import org.apache.spark.sql.SparkSession
import za.co.absa.atum.database.balta.classes.DBConnection

import java.sql.DriverManager
import java.time.{Instant, ZoneId}
import java.time.format.DateTimeFormatter
import java.util.Properties

object AtumE2eTests /*extends Logging*/ {

  private val now = DateTimeFormatter
    .ofPattern("yyyy-MM-dd_HH:mm:ss")
    .withZone(ZoneId.of("UTC"))
    .format(Instant.now())

  private def logInfo(msg: String): Unit = {
    println(msg)
  }

  def main(args: Array[String]): Unit = {
    val jobName = "Atum E2E tests"

    implicit val spark: SparkSession = obtainSparkSession(jobName)
    implicit val dbConnection: DBConnection = obtainDBConnection
    logInfo("DB connection established")

    val partitionsValues = List(
      ("data", jobName),
      ("when", now)
    )
    val subpartitionsValues = List(
      ("foo", "bar")
    )

    val testSuite = new TestSuite(jobName)
    testSuite.test1(partitionsValues)
    logInfo("Test 1 passed")
    testSuite.test2(partitionsValues, subpartitionsValues)
    logInfo("Test 2 passed")
    logInfo("All tests passed")
  }

  private def obtainSparkSession(jobName: String): SparkSession = {
    val spark = SparkSession.builder()
      .appName(jobName)
      .config("spark.master", "local")
      .getOrCreate()

    spark
  }

  private def obtainDBConnection: DBConnection = {
    val config: Config = ConfigFactory.load()

    val dbUrl = config.getString("database.jdbc.url")
    val username = config.getString("database.jdbc.username")
    val password = config.getString("database.jdbc.password")

    val conn = DriverManager.getConnection(dbUrl, username, password)
    conn.setAutoCommit(false)
    new DBConnection(conn)
  }
}
