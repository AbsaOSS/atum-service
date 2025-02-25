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

import java.time.OffsetDateTime
import java.util.UUID

class WriteCheckpointIntegrationTests extends DBTestSuite {

  private val fnWriteCheckpoint = "runs.write_checkpoint"

  private val partitioning = JsonBString(
    """
      |{
      |  "version": 1,
      |  "keys": ["key1", "key3", "key2", "key4"],
      |  "keysToValuesMap": {
      |    "key1": "valueX",
      |    "key2": "valueY",
      |    "key3": "valueZ",
      |    "key4": "valueA"
      |  }
      |}
      |""".stripMargin
  )

  test("Write new checkpoint without data") {
    val uuid = UUID.randomUUID
    val startTime = OffsetDateTime.parse("1992-08-03T10:00:00Z")
    val endTime = OffsetDateTime.parse("2022-11-05T08:00:00Z")


    table("runs.partitionings").insert(
      add("partitioning", partitioning)
        .add("created_by", "John von Neumann")
    )
    //DBTable's insert doesn't return the values yet correctly
    val fkPartitioning: Long = table("runs.partitionings").fieldValue("partitioning", partitioning, "id_partitioning").get.get

    table("runs.measure_definitions").insert(
      add("fk_partitioning", fkPartitioning)
        .add("measure_name", "avg")
        .add("measured_columns", CustomDBType("""{"col1"}""", "TEXT[]"))
        .add("created_by", "Aristoteles")
    )

    assert(table("runs.checkpoints").count(add("fk_partitioning", fkPartitioning)) == 0)

    function(fnWriteCheckpoint)
      .setParam("i_partitioning", partitioning)
      .setParam("i_id_checkpoint", uuid)
      .setParam("i_checkpoint_name", "Empty path")
      .setParam("i_process_start_time", startTime)
      .setParam("i_process_end_time", endTime)
      .setParam("i_measurements", CustomDBType("{}", "JSONB[]"))
      .setParam("i_measured_by_atum_agent", true)
      .setParam("i_by_user", "J. Robert Oppenheimer")
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("Checkpoint created"))
      }

    assert(table("runs.measure_definitions").count(add("fk_partitioning", fkPartitioning)) == 1)
    assert(table("runs.measurements").count(add("fk_checkpoint", uuid)) == 0)
    assert(table("runs.checkpoints").count(add("fk_partitioning", fkPartitioning)) == 1)
    table("runs.checkpoints").where(add("fk_partitioning", fkPartitioning)) {resultSet =>
      val row = resultSet.next()
      assert(row.getString("checkpoint_name").contains("Empty path"))
      assert(row.getOffsetDateTime("process_start_time").contains(startTime))
      assert(row.getOffsetDateTime("process_end_time").contains(endTime))
      assert(row.getBoolean("measured_by_atum_agent").contains(true))
      assert(row.getString("created_by").contains("J. Robert Oppenheimer"))
      assert(row.getOffsetDateTime("created_at").contains(now()))
    }

  }

  test("Write new checkpoint"){
    val uuid = UUID.randomUUID
    val user = "Franz Kafka"
    val startTime = OffsetDateTime.parse("1992-08-03T10:00:00Z")
    val endTime = OffsetDateTime.parse("2022-11-05T08:00:00Z")
    val measurements =
      """
        |{
        |  "{
        |    \"measure\": {
        |      \"measureName\": \"count\",
        |      \"measuredColumns\": []
        |    },
        |    \"result\":{
        |      \"value\":\"3\",
        |      \"type\":\"int\"
        |    }
        |  }",
        |  "{
        |    \"measure\": {
        |      \"measureName\": \"avg\",
        |      \"measuredColumns\": [\"col1\"]
        |    },
        |    \"result\":{
        |      \"value\":\"3.14\",
        |      \"type\":\"double\"
        |    }
        |  }",
        |  "{
        |    \"measure\": {
        |      \"measureName\": \"avg\",
        |      \"measuredColumns\": [\"a\",\"b\"]
        |    },
        |    \"result\":{
        |      \"value\":\"2.71\",
        |      \"type\":\"double\"
        |    }
        |  }"
        |}
        |""".stripMargin

    table("runs.partitionings").insert(
      add("partitioning", partitioning)
        .add("created_by", user)
    )
    //DBTable's insert doesn't return the values yet correctly
    val fkPartitioning: Long = table("runs.partitionings").fieldValue("partitioning", partitioning, "id_partitioning").get.get

    table("runs.measure_definitions").insert(
      add("fk_partitioning", fkPartitioning)
        .add("measure_name", "avg")
        .add("measured_columns", CustomDBType("""{"col1"}""", "TEXT[]"))
        .add("created_by", "Aristoteles")
    )

    assert(table("runs.checkpoints").count(add("fk_partitioning", fkPartitioning)) == 0)

    function(fnWriteCheckpoint)
      .setParam("i_partitioning", partitioning)
      .setParam("i_id_checkpoint", uuid)
      .setParam("i_checkpoint_name", "Happy path")
      .setParam("i_process_start_time", startTime)
      .setParam("i_process_end_time", endTime)
      .setParam("i_measurements", CustomDBType(measurements, "JSONB[]"))
      .setParam("i_measured_by_atum_agent", false)
      .setParam("i_by_user", user)
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("Checkpoint created"))
      }

    assert(table("runs.checkpoints").count(add("fk_partitioning", fkPartitioning)) == 1)
    assert(table("runs.measure_definitions").count(add("fk_partitioning", fkPartitioning)) == 3)
    assert(table("runs.measurements").count(add("fk_checkpoint", uuid)) == 3)
    table("runs.checkpoints").where(add("fk_partitioning", fkPartitioning)) { resultSet =>
      val row = resultSet.next()
      assert(row.getString("checkpoint_name").contains("Happy path"))
      assert(row.getOffsetDateTime("process_start_time").contains(startTime))
      assert(row.getOffsetDateTime("process_end_time").contains(endTime))
      assert(row.getBoolean("measured_by_atum_agent").contains(false))
      assert(row.getString("created_by").contains(user))
      assert(row.getOffsetDateTime("created_at").contains(now()))
    }

    val measureDefinitionIds = table("runs.measure_definitions")
      .where(add("fk_partitioning", fkPartitioning),"id_measure_definition") { resultSet =>
        val row1 = resultSet.next()
        val result1: Vector[Long] = Vector(row1.getLong("id_measure_definition").get)
        assert(row1.getString("measure_name").contains("avg"))
        assert(row1.getArray[String]("measured_columns").map(_.toList).contains(List("col1")))
        assert(row1.getString("created_by").contains("Aristoteles"))
        assert(row1.getOffsetDateTime("created_at").contains(now()))
        val row2 = resultSet.next()
        val result2: Vector[Long] = result1 :+ row2.getLong("id_measure_definition").get
        assert(row2.getString("measure_name").contains("count"))
        assert(row2.getArray[String]("measured_columns").map(_.toList).contains(List.empty))
        assert(row2.getString("created_by").contains(user))
        assert(row2.getOffsetDateTime("created_at").contains(now()))
        val row3 = resultSet.next()
        val result3: Vector[Long] = result2 :+ row3.getLong("id_measure_definition").get
        assert(row3.getString("measure_name").contains("avg"))
        assert(row3.getArray[String]("measured_columns").map(_.toList).contains(List("a", "b")))
        assert(row3.getString("created_by").contains(user))
        assert(row3.getOffsetDateTime("created_at").contains(now()))
        result3
      }
    table("runs.measurements").where(add("fk_checkpoint", uuid), "id_measurement") { resultSet =>
      val row1 = resultSet.next()
      // because measure definition of `count` was created only after the manual enter of the `avg`, it's actually only
      // second in the list
      assert(row1.getLong("fk_measure_definition").contains(measureDefinitionIds(1)))
      assert(row1.getJsonB("measurement_value").contains(JsonBString("""{"type": "int", "value": "3"}""")))
      val row2 = resultSet.next()
      assert(row2.getLong("fk_measure_definition").contains(measureDefinitionIds(0)))
      assert(row2.getJsonB("measurement_value").contains(JsonBString("""{"type": "double", "value": "3.14"}""")))
      val row3 = resultSet.next()
      assert(row3.getLong("fk_measure_definition").contains(measureDefinitionIds(2)))
      assert(row3.getJsonB("measurement_value").contains(JsonBString("""{"type": "double", "value": "2.71"}""")))
    }
  }

  test("Checkpoint already exists") {
    val uuid = UUID.randomUUID
    val origAuthor = "John von Neumann"
    table("runs.partitionings").insert(
      add("partitioning", partitioning)
        .add("created_by", origAuthor)
    )

    //DBTable's insert doesn't return the values yet correctly
    val fkPartitioning: Long = table("runs.partitionings").fieldValue("partitioning", partitioning, "id_partitioning").get.get

    table("runs.checkpoints").insert(
      add("id_checkpoint", uuid)
        .add("fk_partitioning", fkPartitioning)
        .add("checkpoint_name", "I came before")
        .add("process_start_time", now())
        .add("process_end_time", now())
        .add("measured_by_atum_agent", false)
        .add("created_by", origAuthor)
    )

    function(fnWriteCheckpoint)
      .setParam("i_partitioning", partitioning)
      .setParam("i_id_checkpoint", uuid)
      .setParam("i_checkpoint_name", "Won't go in")
      .setParam("i_process_start_time", now())
      .setParamNull("i_process_end_time")
      .setParamNull("i_measurements")
      .setParam("i_measured_by_atum_agent", true)
      .setParam("i_by_user", "J. Robert Oppenheimer")
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(31))
        assert(row.getString("status_text").contains("Checkpoint already present"))
      }

    table("runs.checkpoints").where(add("id_checkpoint", uuid)){queryResult =>
      val row = queryResult.next()
      assert(row.getString("checkpoint_name").contains("I came before"))
      assert(row.getBoolean("measured_by_atum_agent").contains(false))
      assert(row.getString("created_by").contains(origAuthor))
    }
  }

  test("Partitioning of the checkpoint does not exist") {
    val uuid = UUID.randomUUID
    val count = table("runs.checkpoints").count()
    function(fnWriteCheckpoint)
      .setParam("i_partitioning", partitioning)
      .setParam("i_id_checkpoint", uuid)
      .setParam("i_checkpoint_name", "Won't go in")
      .setParam("i_process_start_time", now())
      .setParamNull("i_process_end_time")
      .setParamNull("i_measurements")
      .setParam("i_measured_by_atum_agent", true)
      .setParam("i_by_user", "J. Robert Oppenheimer")
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(41))
        assert(row.getString("status_text").contains("Partitioning not found"))
      }
    assert(table("runs.checkpoints").count() == count)
  }
}
