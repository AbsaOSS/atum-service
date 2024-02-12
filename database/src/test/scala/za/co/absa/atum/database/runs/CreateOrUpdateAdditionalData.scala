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

class CreateOrUpdateAdditionalData extends DBTestSuite{

  private val fncCreateOrUpdateAdditionalData = "runs.create_or_update_additional_data"

  private val partitioning = JsonBString(
    """
      |{
      |  "version": 1,
      |  "keys": ["key1", "key2", "key3"],
      |  "keysToValues": {
      |    "key1": "valueA",
      |    "key2": "valueB",
      |    "key3": "valueC"
      |  }
      |}
      |""".stripMargin
  )

  test("Partitioning and AD present, multiple AD records backed up") {

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
    val inputADToBackUp = CustomDBType(
      """
        |"PrimaryOwner" => "TechnicalManagerA",
        |"SecondaryOwner" => "AnalystNew",
        |"IsDatasetInDatalake" => "true"
        |""".stripMargin,
      "HSTORE"
    )

    function(fncCreateOrUpdateAdditionalData)
      .setParam("i_partitioning", partitioning)
      .setParam("i_additional_data", inputADToBackUp)
      .setParam("i_by_user", "MikeRusty")
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()

        assert(row.getInt("status").contains(12))
        assert(row.getString("status_text").contains("Additional data have been upserted"))

        assert(!queryResult.hasNext)
      }

    assert(table("runs.additional_data").count(add("fk_partitioning", fkPartitioning)) == 3)
    assert(table("runs.additional_data_history").count(add("fk_partitioning", fkPartitioning)) == 1)
  }

}
