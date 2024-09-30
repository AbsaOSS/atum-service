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

package za.co.absa.atum.database.flows

import io.circe.Json
import io.circe.parser.parse
import za.co.absa.balta.DBTestSuite
import za.co.absa.balta.classes.JsonBString

class GetFlowPartitioningsIntegrationTests extends DBTestSuite {

  private val getFlowPartitioningsFn = "flows.get_flow_partitionings"
  private val createFlowFn = "flows._create_flow"
  private val addToParentFlowsFn = "flows._add_to_parent_flows"

  private val partitioningsTable = "runs.partitionings"

  private val partitioning1 = JsonBString(
    """
      |{
      |   "version": 1,
      |   "keys": ["keyA", "keyB", "keyC"],
      |   "keysToValuesMap": {
      |     "keyA": "valueA",
      |     "keyB": "valueB",
      |     "keyC": "valueC"
      |   }
      |}
      |""".stripMargin
  )

  private val partitioning1Parent = JsonBString(
    """
      |{
      |   "version": 1,
      |   "keys": ["keyA", "keyB"],
      |   "keysToValuesMap": {
      |     "keyA": "valueA",
      |     "keyB": "valueB"
      |   }
      |}
      |""".stripMargin
  )

  private val partitioning2 = JsonBString(
    """
      |{
      |   "version": 1,
      |   "keys": ["keyD", "keyE", "keyF"],
      |   "keysToValuesMap": {
      |     "keyD": "valueD",
      |     "keyE": "valueE",
      |     "keyF": "valueF"
      |   }
      |}
      |""".stripMargin
  )

  var flowIdOfPartitioning1: Long = _
  var flowIdOfParentPartitioning1: Long = _
  var flowIdOfPartitioning2: Long = _
  var flowIdOfPartitioning3: Long = _

  test("Returns partitioning(s) for a given flow") {
    table(partitioningsTable).insert(add("partitioning", partitioning1).add("created_by", "Joseph"))
    table(partitioningsTable).insert(add("partitioning", partitioning1Parent).add("created_by", "Joseph"))
    table(partitioningsTable).insert(add("partitioning", partitioning2).add("created_by", "Joseph"))

    val partId1: Long = table(partitioningsTable)
      .fieldValue("partitioning", partitioning1, "id_partitioning").get.get

    val partId1Parent: Long = table(partitioningsTable)
      .fieldValue("partitioning", partitioning1Parent, "id_partitioning").get.get

    val partId2: Long = table(partitioningsTable)
      .fieldValue("partitioning", partitioning2, "id_partitioning").get.get

    function(createFlowFn)
      .setParam("i_fk_partitioning", partId1)
      .setParam("i_by_user", "Joseph")
      .execute { queryResult =>
        flowIdOfPartitioning1 = queryResult.next().getLong("id_flow").get
      }

    function(createFlowFn)
      .setParam("i_fk_partitioning", partId1Parent)
      .setParam("i_by_user", "Joseph")
      .execute { queryResult =>
        flowIdOfParentPartitioning1 = queryResult.next().getLong("id_flow").get
      }

    function(createFlowFn)
      .setParam("i_fk_partitioning", partId2)
      .setParam("i_by_user", "Joseph")
      .execute { queryResult =>
        flowIdOfPartitioning2 = queryResult.next().getLong("id_flow").get
      }

    function(addToParentFlowsFn)
      .setParam("i_fk_parent_partitioning", partId1Parent)
      .setParam("i_fk_partitioning", partId1)
      .setParam("i_by_user", "Joseph")
      .execute { queryResult =>
        val result1 = queryResult.next()
        assert(result1.getInt("status").get == 11)
        assert(result1.getString("status_text").get == "Partitioning added to flows")
      }

    function(getFlowPartitioningsFn)
      .setParam("i_flow_id", flowIdOfPartitioning1)
      .setParam("i_limit", 1)
      .execute { queryResult =>
        val result1 = queryResult.next()
        assert(result1.getInt("status").get == 11)
        assert(result1.getString("status_text").get == "OK")
        assert(result1.getLong("id").get == partId1)
        val expectedPartitioningJson = parseJsonBStringOrThrow(partitioning1)
        val returnedPartitioningJson = parseJsonBStringOrThrow(result1.getJsonB("partitioning").get)
        assert(expectedPartitioningJson == returnedPartitioningJson)
        assert(!result1.getBoolean("has_more").get)
        assert(!queryResult.hasNext)
      }

    function(getFlowPartitioningsFn)
      .setParam("i_flow_id", flowIdOfParentPartitioning1)
      .setParam("i_limit", 1) // limit is set to 1, so only one partitioning should be returned and more data available
      .execute { queryResult =>
        val result1 = queryResult.next()
        assert(result1.getInt("status").get == 11)
        assert(result1.getString("status_text").get == "OK")
        assert(result1.getLong("id").get == partId1)
        val expectedPartitioningJson1 = parseJsonBStringOrThrow(partitioning1)
        val returnedPartitioningJson1 = parseJsonBStringOrThrow(result1.getJsonB("partitioning").get)
        assert(expectedPartitioningJson1 == returnedPartitioningJson1)
        assert(result1.getBoolean("has_more").get)
        assert(!queryResult.hasNext)
      }

    function(getFlowPartitioningsFn)
      .setParam("i_flow_id", flowIdOfParentPartitioning1)
      .setParam("i_limit", 2) // limit is set to 2, so both partitionings should be returned and no more data available
      .execute { queryResult =>
        val result1 = queryResult.next()
        assert(result1.getInt("status").get == 11)
        assert(result1.getString("status_text").get == "OK")
        assert(result1.getLong("id").get == partId1)
        val expectedPartitioningJson1 = parseJsonBStringOrThrow(partitioning1)
        val returnedPartitioningJson1 = parseJsonBStringOrThrow(result1.getJsonB("partitioning").get)
        assert(expectedPartitioningJson1 == returnedPartitioningJson1)
        assert(!result1.getBoolean("has_more").get)
        assert(queryResult.hasNext)
        assert(queryResult.hasNext)
        val result2 = queryResult.next()
        assert(result2.getLong("id").get == partId1Parent)
        val expectedPartitioningJson2 = parseJsonBStringOrThrow(partitioning1Parent)
        val returnedPartitioningJson2 = parseJsonBStringOrThrow(result2.getJsonB("partitioning").get)
        assert(expectedPartitioningJson2 == returnedPartitioningJson2)
        assert(!result2.getBoolean("has_more").get)
        assert(!queryResult.hasNext)
      }
  }

  test("Fails for non-existent flow"){
    function(getFlowPartitioningsFn)
      .setParam("i_flow_id", 999999)
      .setParam("i_limit", 1)
      .execute { queryResult =>
        val result1 = queryResult.next()
        assert(result1.getInt("status").get == 41)
        assert(result1.getString("status_text").get == "Flow not found")
        assert(!queryResult.hasNext)
      }
  }

  private def parseJsonBStringOrThrow(jsonBString: JsonBString): Json = {
    parse(jsonBString.value).getOrElse(throw new Exception("Failed to parse JsonBString to Json"))
  }

}
