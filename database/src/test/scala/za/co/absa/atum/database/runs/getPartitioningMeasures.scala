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

import scala.Byte.MaxValue

class getPartitioningMeasures extends DBTestSuite{

  private val fncGetPartitioningMeasures = "runs.get_partitioning_measures"

  private val partitioning = JsonBString(
    """
      |{
      |  "measure1": ["column1", "column2"],
      |  "measure2": ["column3", "column4"]
      |}
      |""".stripMargin
  )

  test("Get partitioning measures") {
    function(fncGetPartitioningMeasures)
      .setParam("i_partitioning", partitioning)
      .execute { queryResult =>
        assert(queryResult.hasNext)
        val row = queryResult.next()
        assert(row.getArray("measure_column").asInstanceOf[Array[String]].sameElements(Array("column1", "column2")))

        assert(queryResult.hasNext)
        val row2 = queryResult.next()
        assert(row2.getArray("measure_column").asInstanceOf[Array[String]].sameElements(Array("column3", "column4")))

        assert(!queryResult.hasNext)
      }
  }



}
