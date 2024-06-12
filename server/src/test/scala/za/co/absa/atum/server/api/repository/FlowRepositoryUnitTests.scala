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

package za.co.absa.atum.server.api.repository

import org.mockito.Mockito.{mock, when}
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.database.flows.functions.GetFlowCheckpoints
import za.co.absa.atum.server.api.exception.DatabaseError
import zio._
import zio.test.Assertion.failsWithA
import zio.test._

object FlowRepositoryUnitTests extends ZIOSpecDefault with TestData {

  private val getFlowCheckpointsMock = mock(classOf[GetFlowCheckpoints])

  when(getFlowCheckpointsMock.apply(checkpointQueryDTO1)).thenReturn(ZIO.fail(new Exception("boom!")))
  when(getFlowCheckpointsMock.apply(checkpointQueryDTO2)).thenReturn(ZIO.succeed(Seq(checkpointFromDB1, checkpointFromDB2)))

  private val getFlowCheckpointsMockLayer = ZLayer.succeed(getFlowCheckpointsMock)

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    suite("FlowRepositoryIntegrationSuite")(
      suite("GetFlowCheckpointsSuite")(
        test("Returns expected DatabaseError") {
          assertZIO(FlowRepository.getFlowCheckpoints(checkpointQueryDTO1).exit)(
            failsWithA[DatabaseError]
          )
        },
        test("Returns expected Left with StatusException") {
          for {
            result <- FlowRepository.getFlowCheckpoints(checkpointQueryDTO2)
          } yield assertTrue(result == Seq(checkpointFromDB1, checkpointFromDB2))
        },
      ),
    ).provide(
      FlowRepositoryImpl.layer,
      getFlowCheckpointsMockLayer,
    )

  }

}
