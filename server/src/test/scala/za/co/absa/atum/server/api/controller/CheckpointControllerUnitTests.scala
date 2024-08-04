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

package za.co.absa.atum.server.api.controller

import org.mockito.Mockito.{mock, when}
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.exception.ServiceError
import za.co.absa.atum.server.api.service.CheckpointService
import za.co.absa.atum.server.model.InternalServerErrorResponse
import zio.test.Assertion.failsWithA
import zio._
import zio.test._

object CheckpointControllerUnitTests extends ZIOSpecDefault with TestData {

  private val checkpointServiceMock = mock(classOf[CheckpointService])

  when(checkpointServiceMock.saveCheckpoint(checkpointDTO1)).thenReturn(ZIO.succeed(()))
  when(checkpointServiceMock.saveCheckpoint(checkpointDTO2))
    .thenReturn(ZIO.fail(ServiceError("error in data")))
  when(checkpointServiceMock.saveCheckpoint(checkpointDTO3))
    .thenReturn(ZIO.fail(ServiceError("boom!")))

  private val checkpointServiceMockLayer = ZLayer.succeed(checkpointServiceMock)

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    suite("CheckpointControllerIntegrationSuite")(
      suite("CreateCheckpointSuite")(
        test("Returns expected CheckpointDTO") {
          for {
            result <- CheckpointController.createCheckpointV1(checkpointDTO1)
          } yield assertTrue(result == checkpointDTO1)
        },
        test("Returns expected InternalServerErrorResponse") {
          assertZIO(CheckpointController.createCheckpointV1(checkpointDTO3).exit)(
            failsWithA[InternalServerErrorResponse]
          )
        },
        test("Returns expected GeneralErrorResponse") {
          assertZIO(CheckpointController.createCheckpointV1(checkpointDTO2).exit)(
            failsWithA[InternalServerErrorResponse]
          )
        }
      )
    ).provide(
      CheckpointControllerImpl.layer,
      checkpointServiceMockLayer
    )

  }

}
