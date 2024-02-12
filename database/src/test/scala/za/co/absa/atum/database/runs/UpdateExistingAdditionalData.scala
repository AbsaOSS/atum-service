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

class UpdateExistingAdditionalData extends DBTestSuite{

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
      add("fk_partitioning", fkPartitioning)
      .add("ad_name", "DatasetLocation")
      .add("ad_value", "s3://some_bucket/some_path")
      .add("created_by", "MikeRusty")
    )
    table("runs.additional_data").insert(
      add("fk_partitioning", fkPartitioning)
        .add("ad_name", "Author")
        .add("ad_value", "Walker")
        .add("created_by", "MikeRusty")
    )
    val inputADToBackUp = CustomDBType(
      """
        |"DatasetLocation" => "s3://some_bucket/some_path2",
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

        val adWasBackedUp = row.getBoolean("ad_backup_performed").get
        assert(adWasBackedUp)

        assert(!queryResult.hasNext)
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

        val adWasBackedUp = row.getBoolean("ad_backup_performed").get
        assert(!adWasBackedUp)

        assert(!queryResult.hasNext)
      }
  }
}
