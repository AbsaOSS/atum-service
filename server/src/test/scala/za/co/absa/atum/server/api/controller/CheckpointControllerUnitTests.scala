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
import za.co.absa.atum.model.dto.{CheckpointDTO, CheckpointV2DTO}
import za.co.absa.atum.server.ConfigProviderTest
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.exception.ServiceError._
import za.co.absa.atum.server.api.http.ApiPaths.V2Paths
import za.co.absa.atum.server.api.service.CheckpointService
import za.co.absa.atum.server.config.SslConfig
import za.co.absa.atum.server.model.{ConflictErrorResponse, InternalServerErrorResponse, NotFoundErrorResponse}
import za.co.absa.atum.server.model.SuccessResponse.SingleSuccessResponse
import zio.test.Assertion.failsWithA
import zio._
import zio.test._

object CheckpointControllerUnitTests extends ConfigProviderTest with TestData {

  private val checkpointServiceMock = mock(classOf[CheckpointService])

  when(checkpointServiceMock.saveCheckpoint(checkpointDTO1)).thenReturn(ZIO.unit)
  when(checkpointServiceMock.saveCheckpoint(checkpointDTO2))
    .thenReturn(ZIO.fail(GeneralServiceError("error in data")))
  when(checkpointServiceMock.saveCheckpoint(checkpointDTO3))
    .thenReturn(ZIO.fail(GeneralServiceError("boom!")))

  private val partitioningId = 1L

  when(checkpointServiceMock.saveCheckpointV2(partitioningId, checkpointV2DTO1)).thenReturn(ZIO.unit)
  when(checkpointServiceMock.saveCheckpointV2(partitioningId, checkpointV2DTO2))
    .thenReturn(ZIO.fail(GeneralServiceError("error in data")))
  when(checkpointServiceMock.saveCheckpointV2(partitioningId, checkpointV2DTO3))
    .thenReturn(ZIO.fail(ConflictServiceError("boom!")))

  when(checkpointServiceMock.getCheckpointV2(partitioningId, checkpointV2DTO1.id))
    .thenReturn(ZIO.succeed(checkpointV2DTO1))
  when(checkpointServiceMock.getCheckpointV2(partitioningId, checkpointV2DTO2.id))
    .thenReturn(ZIO.fail(NotFoundServiceError("not found")))
  when(checkpointServiceMock.getCheckpointV2(partitioningId, checkpointV2DTO3.id))
    .thenReturn(ZIO.fail(GeneralServiceError("boom!")))

  private val checkpointServiceMockLayer = ZLayer.succeed(checkpointServiceMock)

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    suite("CheckpointControllerIntegrationSuite")(
      suite("CreateCheckpointSuite")(
        test("Returns expected CheckpointDTO") {
          for {
            result <- CheckpointController.createCheckpointV1(checkpointDTO1)
          } yield assertTrue(result == checkpointDTO1)
        },
        test("Returns expected ConflictServiceError") {
          assertZIO(CheckpointController.createCheckpointV1(checkpointDTO2).exit)(
            failsWithA[InternalServerErrorResponse]
          )
        },
        test("Returns expected ConflictServiceError") {
          assertZIO(CheckpointController.createCheckpointV1(checkpointDTO3).exit)(
            failsWithA[InternalServerErrorResponse]
          )
        }
      ),
      suite("PostCheckpointV2Suite")(
        test("Returns expected CheckpointDTO") {
          val host = "testHost"
          for {
            _ <- TestSystem.putEnv("HOSTNAME", host)
            sslConfig <- ZIO.config[SslConfig](SslConfig.config)
            result <- CheckpointController.postCheckpointV2(partitioningId, checkpointV2DTO1)
            protocol = if (sslConfig.enabled) "https" else "http"
            port = if (sslConfig.enabled) 8443 else 8080
            path = s"${V2Paths.Partitionings}/$partitioningId/${V2Paths.Checkpoints}/${checkpointV2DTO1.id}"
            expectedUri = s"$protocol://$host:$port/$path"
          } yield assertTrue(
            result._1.isInstanceOf[SingleSuccessResponse[CheckpointV2DTO]]
              && result._1.data == checkpointV2DTO1
              && result._2 == expectedUri
          )
        },
        test("Returns expected ConflictServiceError") {
          assertZIO(CheckpointController.postCheckpointV2(1L, checkpointV2DTO2).exit)(
            failsWithA[InternalServerErrorResponse]
          )
        },
        test("Returns expected ConflictServiceError") {
          assertZIO(CheckpointController.postCheckpointV2(1L, checkpointV2DTO3).exit)(
            failsWithA[ConflictErrorResponse]
          )
        }
      ),
      suite("GetPartitioningCheckpointV2Suite")(
        test("Returns expected CheckpointDTO") {
          for {
            result <- CheckpointController.getPartitioningCheckpointV2(partitioningId, checkpointV2DTO1.id)
          } yield assertTrue(result.data == checkpointV2DTO1)
        },
        test("Returns expected NotFoundErrorResponse"){
          assertZIO(CheckpointController.getPartitioningCheckpointV2(partitioningId, checkpointV2DTO2.id).exit)(
            failsWithA[NotFoundErrorResponse]
          )
        },
        test("Returns expected InternalServerErrorResponse"){
          assertZIO(CheckpointController.getPartitioningCheckpointV2(partitioningId, checkpointV2DTO3.id).exit)(
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
