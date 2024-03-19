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

class UpdateExistingAdditionalDataTest extends DBTestSuite{

  private val fncUpdateExistingAdditionalData = "runs._update_existing_additional_data"

  private val partitioning = JsonBString(
    """
    |{
    |  "version": 1,
    |  "keys": ["key1", "key2", "key3"],
    |  "keysToValues": {
    |    "key1": "valueX",
    |    "key2": "valueY",
    |    "key3": "valueZ"
    |  }
    |}
    |""".stripMargin
  )

  test("Partitioning and AD present, multiple AD records backed up") {

    table("runs.partitionings").insert(
      add("partitioning", partitioning)
      .add("created_by", "MikeRusty")
    )

    //DBTable's insert doesn't return the values yet correctly
    val fkPartitioning: Long = table("runs.partitionings").fieldValue("partitioning", partitioning, "id_partitioning").get.get

    table("runs.additional_data").insert(
      add("id_additional_data", 0L)
      .add("fk_partitioning", fkPartitioning)
      .add("ad_name", "DatasetLocation")
      .add("ad_value", "s3://some_bucket/some_path")
      .add("created_by", "Laco")
    )
    table("runs.additional_data").insert(
      add("id_additional_data", 1L)
        .add("fk_partitioning", fkPartitioning)
        .add("ad_name", "Author")
        .add("ad_value", "Walker")
        .add("created_by", "MikeRusty")
    )
    val inputADToBackUp = CustomDBType(
      """
        |"DatasetLocation" => "s3://some_bucket/some_path_updated",
        |"Author" => "NewOne"
        |""".stripMargin,
      "HSTORE"
    )

    function(fncUpdateExistingAdditionalData)
      .setParam("i_fk_partitioning", fkPartitioning)
      .setParam("i_additional_data", inputADToBackUp)
      .setParam("i_by_user", "MikeRusty")
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()

        val adWasBackedUp = row.getBoolean("records_updated").get
        assert(adWasBackedUp)

        assert(!queryResult.hasNext)
      }

    assert(table("runs.additional_data").count() == 2)
    assert(table("runs.additional_data_history").count() == 2)

    assert(table("runs.additional_data").count(add("fk_partitioning", fkPartitioning)) == 2)
    assert(table("runs.additional_data_history").count(add("fk_partitioning", fkPartitioning)) == 2)

    val expectedDataInAdTable = Seq(
      (0L, "DatasetLocation", "s3://some_bucket/some_path", "Laco"),
      (1L, "Author", "Walker", "MikeRusty"),
    )
    expectedDataInAdTable.foreach { case (adIdExp, adNameExp, adValExp, adCreatedByExp) =>
      table("runs.additional_data_history").where(add("ad_name", adNameExp)) {
        resultSet =>
          val row = resultSet.next()
          assert(row.getLong("id_additional_data").contains(adIdExp))
          assert(row.getString("ad_value").contains(adValExp))
          assert(row.getString("created_by_originally").contains(adCreatedByExp))
      }
    }

    val expectedDataInAdHistTable = Seq(
      ("DatasetLocation", "s3://some_bucket/some_path_updated", "MikeRusty"),
      ("Author", "NewOne", "MikeRusty"),
    )
    expectedDataInAdHistTable.foreach { case (adNameExp, adValExp, adCreatedByExp) =>
      table("runs.additional_data").where(add("ad_name", adNameExp)) {
        resultSet =>
          val row = resultSet.next()
          assert(!row.getLong("id_additional_data").contains(0L))
          assert(!row.getLong("id_additional_data").contains(1L))
          assert(row.getString("ad_value").contains(adValExp))
          assert(row.getString("created_by").contains(adCreatedByExp))
      }
    }

  }

  test("Partitioning and AD present, but the input AD are the same as in DB, no backup") {

    table("runs.partitionings").insert(
      add("partitioning", partitioning)
        .add("created_by", "MikeRusty")
    )

    //DBTable's insert doesn't return the values yet correctly
    val fkPartitioning: Long = table("runs.partitionings").fieldValue("partitioning", partitioning, "id_partitioning").get.get

    table("runs.additional_data").insert(
      add("fk_partitioning", fkPartitioning)
        .add("ad_name", "Ownership")
        .add("ad_value", "Primary")
        .add("created_by", "Bot")
    )
    table("runs.additional_data").insert(
      add("fk_partitioning", fkPartitioning)
        .add("ad_name", "Author")
        .add("ad_value", "Matthew")
        .add("created_by", "Bot")
    )
    val inputADToBackUp = CustomDBType(
      """
        |"Ownership" => "Primary",
        |"Author" => "Matthew",
        |"Redundant" => "ShouldBeIgnored"
        |""".stripMargin,
      "HSTORE"
    )

    function(fncUpdateExistingAdditionalData)
      .setParam("i_fk_partitioning", fkPartitioning)
      .setParam("i_additional_data", inputADToBackUp)
      .setParam("i_by_user", "MikeRusty")
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()

        val adWasBackedUp = row.getBoolean("records_updated").get
        assert(!adWasBackedUp)

        assert(!queryResult.hasNext)
      }

    assert(table("runs.additional_data").count(add("fk_partitioning", fkPartitioning)) == 2)
    assert(table("runs.additional_data_history").count(add("fk_partitioning", fkPartitioning)) == 0)
  }
}
