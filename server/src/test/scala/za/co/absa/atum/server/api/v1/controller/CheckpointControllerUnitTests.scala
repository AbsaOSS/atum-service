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

package za.co.absa.atum.server.api.v1.controller

import org.mockito.Mockito.{mock, when}
import za.co.absa.atum.model.envelopes.{ConflictErrorResponse, InternalServerErrorResponse}
import za.co.absa.atum.server.ConfigProviderTest
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.exception.ServiceError.{ConflictServiceError, GeneralServiceError}
import za.co.absa.atum.server.api.v1.service.CheckpointService
import zio._
import zio.test.Assertion.failsWithA
import zio.test._

object CheckpointControllerUnitTests extends ConfigProviderTest with TestData {

  private val checkpointServiceMock = mock(classOf[CheckpointService])

  when(checkpointServiceMock.saveCheckpoint(checkpointDTO1)).thenReturn(ZIO.unit)
  when(checkpointServiceMock.saveCheckpoint(checkpointDTO2))
    .thenReturn(ZIO.fail(GeneralServiceError("error in data")))
  when(checkpointServiceMock.saveCheckpoint(checkpointDTO3))
    .thenReturn(ZIO.fail(ConflictServiceError("boom!")))

  private val checkpointServiceMockLayer = ZLayer.succeed(checkpointServiceMock)

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    suite("CheckpointControllerIntegrationSuite")(
      suite("CreateCheckpointSuite")(
        test("Returns expected CheckpointDTO") {
          for {
            result <- CheckpointController.createCheckpoint(checkpointDTO1)
          } yield assertTrue(result == checkpointDTO1)
        },
        test("Returns expected ConflictServiceError") {
          assertZIO(CheckpointController.createCheckpoint(checkpointDTO2).exit)(
            failsWithA[InternalServerErrorResponse]
          )
        },
        test("Returns expected ConflictServiceError") {
          assertZIO(CheckpointController.createCheckpoint(checkpointDTO3).exit)(
            failsWithA[ConflictErrorResponse]
          )
        }
      ),
    ).provide(
      CheckpointControllerImpl.layer,
      checkpointServiceMockLayer
    )

  }

}
