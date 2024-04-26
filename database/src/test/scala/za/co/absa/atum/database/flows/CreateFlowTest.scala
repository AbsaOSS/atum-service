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

import za.co.absa.balta.DBTestSuite

import scala.util.Random


class CreateFlowTest extends DBTestSuite {
  private val fncCreateFlow = "flows._create_flow"

  test("Create flow") {
    val partitioningId: Long = Random.nextLong()
    val user = "Geralt of Rivia"
    val flowID = function(fncCreateFlow)
      .setParam("i_fk_partitioning", partitioningId)
      .setParam("i_by_user", user)
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getInt("status").contains(11))
        assert(row.getString("status_text").contains("Flow created"))
        val result = row.getLong("id_flow").get
        assert(!queryResult.hasNext)
        result
      }

    table("flows.flows").where(add("id_flow", flowID)){ queryResult =>
      assert(queryResult.hasNext)
      val row = queryResult.next()
      assert(row.getBoolean("from_pattern").contains(false))
      assert(row.getString("created_by").contains(user))
      assert(row.getLong("fk_primary_partitioning").contains(partitioningId))
      assert(!queryResult.hasNext)
    }

    table("flows.partitioning_to_flow").where(add("fk_flow", flowID)){ queryResult =>
      assert(queryResult.hasNext)
      val row = queryResult.next()
      assert(row.getLong("fk_partitioning").contains(partitioningId))
      assert(row.getString("created_by").contains(user))
      assert(!queryResult.hasNext)
    }
  }
}
