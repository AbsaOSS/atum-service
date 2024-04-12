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

import org.junit.runner.RunWith
import org.mockito.Mockito.{mock, when}
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.exception.ServiceError
import za.co.absa.atum.server.api.service.CheckpointService
import za.co.absa.atum.server.model.{GeneralErrorResponse, InternalServerErrorResponse}
import za.co.absa.fadb.exceptions.ErrorInDataException
import za.co.absa.fadb.status.FunctionStatus
import zio.test.Assertion.failsWithA
import zio._
import zio.test._
import zio.test.junit.ZTestJUnitRunner

@RunWith(classOf[ZTestJUnitRunner])
class CheckpointControllerSpec extends ZIOSpecDefault with TestData {

  private val checkpointServiceMock = mock(classOf[CheckpointService])

  when(checkpointServiceMock.saveCheckpoint(checkpointDTO1)).thenReturn(ZIO.right(()))
  when(checkpointServiceMock.saveCheckpoint(checkpointDTO2))
    .thenReturn(ZIO.left(ErrorInDataException(FunctionStatus(50, "error in data"))))
  when(checkpointServiceMock.saveCheckpoint(checkpointDTO3))
    .thenReturn(ZIO.fail(ServiceError("boom!")))

  private val checkpointServiceMockLayer = ZLayer.succeed(checkpointServiceMock)

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    suite("CheckpointControllerSuite")(
      suite("CreateCheckpointSuite")(
        test("Returns expected CheckpointDTO") {
          for {
            result <- CheckpointController.createCheckpoint(checkpointDTO1)
          } yield assertTrue(result == checkpointDTO1)
        } @@ TestAspect.tag("IntegrationTest"),
        test("Returns expected InternalServerErrorResponse") {
          assertZIO(CheckpointController.createCheckpoint(checkpointDTO3).exit)(failsWithA[InternalServerErrorResponse])
        },
        test("Returns expected GeneralErrorResponse") {
          assertZIO(CheckpointController.createCheckpoint(checkpointDTO2).exit)(failsWithA[GeneralErrorResponse])
        } @@ TestAspect.tag("IntegrationTest")
      )
    ).provide(
      CheckpointControllerImpl.layer,
      checkpointServiceMockLayer
    )

  }

}
