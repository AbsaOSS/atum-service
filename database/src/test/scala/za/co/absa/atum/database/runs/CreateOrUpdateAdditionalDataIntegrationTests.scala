/*
 * Copyright 2021 ABSA Group Limited
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

package za.co.absa.atum.database.runs

import za.co.absa.balta.DBTestSuite
import za.co.absa.balta.classes.JsonBString
import za.co.absa.balta.classes.setter.CustomDBType

class CreateOrUpdateAdditionalDataIntegrationTests extends DBTestSuite {

  private val fncCreateOrUpdateAdditionalData = "runs.create_or_update_additional_data"

  private val partitioning = JsonBString(
    """
      |{
      |  "version": 1,
      |  "keys": ["key1", "key2", "key3"],
      |  "keysToValuesMap": {
      |    "key1": "valueA",
      |    "key2": "valueB",
      |    "key3": "valueC"
      |  }
      |}
      |""".stripMargin
  )

  test("Partitioning and AD present, delete & re-insert, and also 'ignore' of AD records performed") {

    table("runs.partitionings").insert(
      add("partitioning", partitioning)
        .add("created_by", "Hendrix")
    )

    //DBTable's insert doesn't return the values yet correctly
    val fkPartitioning: Long = table("runs.partitionings").fieldValue("partitioning", partitioning, "id_partitioning").get.get

    table("runs.additional_data").insert(
      add("fk_partitioning", fkPartitioning)
        .add("ad_name", "PrimaryOwner")
        .add("ad_value", "TechnicalManagerA")
        .add("created_by", "SuperTool")
    )
    table("runs.additional_data").insert(
      add("fk_partitioning", fkPartitioning)
        .add("ad_name", "SecondaryOwner")
        .add("ad_value", "AnalystB")
        .add("created_by", "SuperTool")
    )
    val inputADToUpsert = CustomDBType(
      """
        |"PrimaryOwner" => "TechnicalManagerA",
        |"SecondaryOwner" => "AnalystNew",
        |"IsDatasetInDatalake" => "true"
        |""".stripMargin,
      "HSTORE"
    )

    function(fncCreateOrUpdateAdditionalData)
      .setParam("i_partitioning_id", fkPartitioning)
      .setParam("i_additional_data", inputADToUpsert)
      .setParam("i_by_user", "MikeRusty")
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()

        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("Additional data have been updated, added or both"))
        assert(row.getString("o_ad_name").contains("PrimaryOwner"))
        assert(row.getString("o_ad_value").contains("TechnicalManagerA"))
        assert(row.getString("o_ad_author").contains("SuperTool"))

        assert(queryResult.hasNext)
        val row2 = queryResult.next()

        assert(row2.getInt("status").contains(11))
        assert(row2.getString("status_text").contains("Additional data have been updated, added or both"))
        assert(row2.getString("o_ad_name").contains("SecondaryOwner"))
        assert(row2.getString("o_ad_value").contains("AnalystNew"))
        assert(row2.getString("o_ad_author").contains("MikeRusty"))

        assert(queryResult.hasNext)
        val row3 = queryResult.next()
        assert(row3.getInt("status").contains(11))
        assert(row3.getString("status_text").contains("Additional data have been updated, added or both"))
        assert(row3.getString("o_ad_name").contains("IsDatasetInDatalake"))
        assert(row3.getString("o_ad_value").contains("true"))
        assert(row3.getString("o_ad_author").contains("MikeRusty"))

        assert(!queryResult.hasNext)
      }

    assert(table("runs.additional_data").count() == 3)
    assert(table("runs.additional_data").count(add("fk_partitioning", fkPartitioning)) == 3)
    assert(table("runs.additional_data_history").count(add("fk_partitioning", fkPartitioning)) == 1)

    val expectedDataInAdTable = Seq(
      ("PrimaryOwner", "TechnicalManagerA", "SuperTool"),
      ("SecondaryOwner", "AnalystNew", "MikeRusty"),
      ("IsDatasetInDatalake", "true", "MikeRusty"),
    )
    expectedDataInAdTable.foreach { case (adNameExp, adValExp, adCreatedByExp) =>
      table("runs.additional_data").where(add("ad_name", adNameExp)) {
        resultSet =>
          val row = resultSet.next()
          assert(row.getString("ad_value").contains(adValExp))
          assert(row.getString("created_by").contains(adCreatedByExp))
      }
    }
  }

  test("Partitioning and AD present, new AD records inserted, nothing backed up") {

    table("runs.partitionings").insert(
      add("partitioning", partitioning)
        .add("created_by", "Jimi")
    )

    //DBTable's insert doesn't return the values yet correctly
    val fkPartitioning: Long = table("runs.partitionings").fieldValue("partitioning", partitioning, "id_partitioning").get.get

    table("runs.additional_data").insert(
      add("fk_partitioning", fkPartitioning)
        .add("ad_name", "PrimaryOwner")
        .add("ad_value", "TechnicalManagerX")
        .add("created_by", "Bot")
    )
    table("runs.additional_data").insert(
      add("fk_partitioning", fkPartitioning)
        .add("ad_name", "SecondaryOwner")
        .add("ad_value", "AnalystY")
        .add("created_by", "Bot")
    )
    val inputADToUpsert = CustomDBType(
      """
        |"SomeNewKey" => "SomeNewValue",
        |"IsDatasetInHDFS" => "true",
        |"DatasetContentSensitivityLevel" => "1"
        |""".stripMargin,
      "HSTORE"
    )

    function(fncCreateOrUpdateAdditionalData)
      .setParam("i_partitioning_id", fkPartitioning)
      .setParam("i_additional_data", inputADToUpsert)
      .setParam("i_by_user", "MikeRusty")
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()

        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("Additional data have been updated, added or both"))
      }

    assert(table("runs.additional_data").count() == 5)
    assert(table("runs.additional_data").count(add("fk_partitioning", fkPartitioning)) == 5)
    assert(table("runs.additional_data_history").count(add("fk_partitioning", fkPartitioning)) == 0)

    val expectedDataInAdTable = Seq(
      ("PrimaryOwner", "TechnicalManagerX", "Bot"),
      ("SecondaryOwner", "AnalystY", "Bot"),
      ("SomeNewKey", "SomeNewValue", "MikeRusty"),
      ("IsDatasetInHDFS", "true", "MikeRusty"),
      ("DatasetContentSensitivityLevel", "1", "MikeRusty"),
    )
    expectedDataInAdTable.foreach { case (adNameExp, adValExp, adCreatedByExp) =>
      table("runs.additional_data").where(add("ad_name", adNameExp)) {
        resultSet =>
          val row = resultSet.next()
          assert(row.getString("ad_value").contains(adValExp))
          assert(row.getString("created_by").contains(adCreatedByExp))
      }
    }
  }

  test("Partitioning and AD present, but no new AD records were backed-up or inserted, no changes detected") {

    table("runs.partitionings").insert(
      add("partitioning", partitioning)
        .add("created_by", "Page")
    )

    //DBTable's insert doesn't return the values yet correctly
    val fkPartitioning: Long = table("runs.partitionings").fieldValue("partitioning", partitioning, "id_partitioning").get.get

    table("runs.additional_data").insert(
      add("fk_partitioning", fkPartitioning)
        .add("ad_name", "PrimaryOwner")
        .add("ad_value", "TechnicalManagerQ")
        .add("created_by", "TechnoKingMusk")
    )
    table("runs.additional_data").insert(
      add("fk_partitioning", fkPartitioning)
        .add("ad_name", "SecondaryOwner")
        .add("ad_value", "AnalystW")
        .add("created_by", "TechnoKingMusk")
    )
    val inputADToUpsert = CustomDBType(
      """
        |"PrimaryOwner" => "TechnicalManagerQ",
        |"SecondaryOwner" => "AnalystW"
        |""".stripMargin,
      "HSTORE"
    )

    function(fncCreateOrUpdateAdditionalData)
      .setParam("i_partitioning_id", fkPartitioning)
      .setParam("i_additional_data", inputADToUpsert)
      .setParam("i_by_user", "MikeRusty")
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()

        assert(row.getInt("status").contains(14))
        assert(row.getString("status_text").contains("No changes in additional data"))
      }

    assert(table("runs.additional_data").count(add("fk_partitioning", fkPartitioning)) == 2)
    assert(table("runs.additional_data_history").count(add("fk_partitioning", fkPartitioning)) == 0)
  }

  test("Partitioning not present, no action taken") {

    val inputADToInsert = CustomDBType(
      """
        |"PrimaryOwner" => "TechnicalManagerA",
        |"SecondaryOwner" => "AnalystNew",
        |"IsDatasetInDatalake" => "true"
        |""".stripMargin,
      "HSTORE"
    )

    function(fncCreateOrUpdateAdditionalData)
      .setParam("i_partitioning_id", 0L)
      .setParam("i_additional_data", inputADToInsert)
      .setParam("i_by_user", "MikeRusty")
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()

        assert(row.getInt("status").contains(41))
        assert(row.getString("status_text").contains("Partitioning not found"))

        assert(!queryResult.hasNext)
      }

    assert(table("runs.additional_data").count() == 0)
    assert(table("runs.additional_data_history").count() == 0)
  }
}
