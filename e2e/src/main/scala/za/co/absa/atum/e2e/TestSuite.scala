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
import za.co.absa.atum.agent.AtumContext.{AtumPartitions, DatasetWrapper}
import za.co.absa.atum.database.balta.classes.{DBConnection, DBTable}

class TestSuite (jobName: String)(implicit spark: SparkSession, dbConnection: DBConnection) {

  def test1(partitionsValues: List[(String, String)]): Unit = {
    val mainPartition = AtumPartitions(partitionsValues)

    implicit val context: AtumContext = AtumAgent.getOrCreateAtumContext(mainPartition)
    //check DB
//    DBTable("").where("") {resultSet =>
//
//    }

    val df1: DataFrame = spark.read.csv(getClass.getResource("/data/table1.csv").getPath)
    df1.createCheckpoint("Read table 1")
    //check DB

  }

  def test2(partitionsValues: List[(String, String)], subpartitionsValues: List[(String, String)]): Unit = {
    val mainPartition = AtumPartitions(partitionsValues)
    val subPartition = AtumPartitions(subpartitionsValues)
    val subContext: AtumContext = AtumAgent.getOrCreateAtumSubContext(subPartition)(AtumAgent.getOrCreateAtumContext(mainPartition))
    ()
    //check DB

    val df2: DataFrame = spark.read.csv(getClass.getResource("/data/table2.csv").getPath)
    df2.createCheckpoint("Read table 2")(subContext)
    //check DB
  }
}
