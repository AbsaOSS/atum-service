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

package za.co.absa.atum.server.api.service

import org.junit.runner.RunWith
import org.mockito.Mockito.{mock, when}
import za.co.absa.atum.model.dto.CheckpointDTO
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.exception.{DatabaseError, ServiceError}
import za.co.absa.atum.server.api.repository.FlowRepository
import zio._
import zio.test.Assertion.failsWithA
import zio.test._
import zio.test.junit.ZTestJUnitRunner

@RunWith(classOf[ZTestJUnitRunner])
class FlowServiceSpec extends ZIOSpecDefault with TestData {
  private val flowRepositoryMock = mock(classOf[FlowRepository])

  when(flowRepositoryMock.getFlowCheckpoints(checkpointQueryDTO1)).thenReturn(ZIO.fail(DatabaseError("boom!")))
  when(flowRepositoryMock.getFlowCheckpoints(checkpointQueryDTO2))
    .thenReturn(ZIO.succeed(Seq(checkpointFromDB2)))

  private val flowRepositoryMockLayer = ZLayer.succeed(flowRepositoryMock)

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    suite("FlowServiceSpec")(
      suite("GetFlowCheckpointsSuite")(
        test("Returns expected ServiceError") {
          assertZIO(FlowService.getFlowCheckpoints(checkpointQueryDTO1).exit)(
            failsWithA[ServiceError]
          )
        },
        test("Returns expected Seq[CheckpointDTO]") {
          for {
            result <- FlowService.getFlowCheckpoints(checkpointQueryDTO2)
          } yield assertTrue{
            result.isInstanceOf[Seq[CheckpointDTO]]
            result == Seq(checkpointDTO2)
          }
        },

      ),
    ).provide(
      FlowServiceImpl.layer,
      flowRepositoryMockLayer
    )

  }
}
