/*
 * Copyright 2024 ABSA Group Limited
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

import org.mockito.Mockito.mock
import org.scalatest.funsuite.{AnyFunSuite, AnyFunSuiteLike}
import za.co.absa.db.fadb.doobie.DoobieEngine
import zio.Task
import za.co.absa.db.fadb.DBSchema
import za.co.absa.db.fadb.naming.implementations.SnakeCaseNaming.Implicits._


class GetPartitioningMeasuresUnitTests extends AnyFunSuiteLike {
  private val schema = new DBSchema("Foo") {}
  private val mockEngine = mock(classOf[DoobieEngine[Task]])
  private val getPartitioningMeasures = new GetPartitioningMeasures()(schema, mockEngine)

  test("GetPartitioningMeasures should have correct fieldsToSelect") {
    assert(getPartitioningMeasures.fieldsToSelect == Seq("status", "status_text", "measure_name", "measured_columns"))
  }
}
