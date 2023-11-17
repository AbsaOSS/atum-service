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

import org.apache.spark.sql.{DataFrame, SparkSession}
import za.co.absa.atum.agent.{AtumAgent, AtumContext}
import za.co.absa.atum.agent.AtumContext.DatasetWrapper

object AtumE2eTests {
  def main(args: Array[String]): Unit = {
    val jobName = "Atum E2E tests"
    implicit val spark: SparkSession = obtainSparkSession(jobName)

    implicit val context: AtumContext = AtumAgent.getOrCreateAtumContext(???)

    val df: DataFrame = ???
    df.createCheckpoint("xxx")

    //implicit val subContext: AtumContext = AtumAgent.getOrCreateAtumSubContext(???)
  }

  private def obtainSparkSession(jobName: String): SparkSession = {
    val spark = SparkSession.builder()
      .appName(jobName)
      .getOrCreate()

    spark
  }
}
