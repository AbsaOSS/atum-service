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

package za.co.absa.atum.server.api.database.runs.functions

import io.circe.Json
import za.co.absa.atum.server.api.database.runs.functions.GetPartitioningAncestors.GetPartitioningAncestorsResult
import za.co.absa.db.fadb.exceptions.DataNotFoundException
import za.co.absa.db.fadb.status.handling.implementations.StandardStatusHandling
import za.co.absa.db.fadb.status.{FunctionStatus, Row}
import zio.test._

/**
 *  Pure status handling of the `GetPartitioningAncestors` wrapper (no DB): the rows the DB function can return
 *  (10 with data, 14 with all-NULL data columns, 41) are fed through the same `checkStatus` the wrapper uses.
 */
object GetPartitioningAncestorsStatusUnitTests extends ZIOSpecDefault {

  private object Handling extends StandardStatusHandling

  private val ancestor = GetPartitioningAncestorsResult(7L, Json.Null, "someone", hasMore = false)

  override def spec: Spec[TestEnvironment, Any] = suite("GetPartitioningAncestorsStatusSuite")(
    test("status 10 with data -> the ancestor") {
      val row = Row[Option[Option[GetPartitioningAncestorsResult]]](FunctionStatus(10, "OK"), Some(Some(ancestor)))
      val result = GetPartitioningAncestors.statusExceptionOrRow(Handling.checkStatus, row)
      assertTrue(result.map(r => (r.functionStatus, r.data)) == Right((FunctionStatus(10, "OK"), Some(ancestor))))
    },
    test("status 14 with no data (existing partitioning without ancestors) -> a data-less row, no exception") {
      val row = Row[Option[Option[GetPartitioningAncestorsResult]]](FunctionStatus(14, "OK"), None)
      val result = GetPartitioningAncestors.statusExceptionOrRow(Handling.checkStatus, row)
      assertTrue(
        result.map(r => (r.functionStatus, r.data)) == Right((FunctionStatus(14, "OK"), None))
      )
    },
    test("status 41 (unknown partitioning) -> DataNotFoundException, whatever the data is") {
      val row = Row[Option[Option[GetPartitioningAncestorsResult]]](FunctionStatus(41, "Partitioning not found"), None)
      val result = GetPartitioningAncestors.statusExceptionOrRow(Handling.checkStatus, row)
      assertTrue(result == Left(DataNotFoundException(FunctionStatus(41, "Partitioning not found"))))
    }
  )
}
