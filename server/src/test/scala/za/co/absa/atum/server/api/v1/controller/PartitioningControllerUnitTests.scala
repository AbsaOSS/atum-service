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
import za.co.absa.atum.model.envelopes.InternalServerErrorResponse
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.exception.ServiceError.{GeneralServiceError, NotFoundServiceError}
import za.co.absa.atum.server.api.v1.service.{PartitioningService => PartitioningServiceV1}
import za.co.absa.atum.server.api.v2.service.{PartitioningService => PartitioningServiceV2}
import zio._
import zio.test.Assertion.failsWithA
import zio.test._

object PartitioningControllerUnitTests extends ZIOSpecDefault with TestData {
  private val partitioningServiceMockV1 = mock(classOf[PartitioningServiceV1])
  private val partitioningServiceMockV2 = mock(classOf[PartitioningServiceV2])

  when(partitioningServiceMockV1.createPartitioningIfNotExists(partitioningSubmitDTO1))
    .thenReturn(ZIO.unit)
  when(partitioningServiceMockV1.createPartitioningIfNotExists(partitioningSubmitDTO2))
    .thenReturn(ZIO.fail(GeneralServiceError("boom!")))

  when(partitioningServiceMockV2.getPartitioningMeasures(partitioningDTO1))
    .thenReturn(ZIO.succeed(Seq(measureDTO1, measureDTO2)))

  when(partitioningServiceMockV2.getPartitioning(partitioningDTO1))
    .thenReturn(ZIO.succeed(partitioningWithIdDTO1))
  when(partitioningServiceMockV2.getPartitioning(partitioningDTO2))
    .thenReturn(ZIO.fail(NotFoundServiceError("Partitioning not found")))
  when(partitioningServiceMockV2.getPartitioning(partitioningDTO3))
    .thenReturn(ZIO.fail(GeneralServiceError("boom!")))

  when(partitioningServiceMockV2.getPartitioningAdditionalData(1L))
    .thenReturn(ZIO.succeed(_additionalDataDTO1))
  when(partitioningServiceMockV2.getPartitioningAdditionalData(2L))
    .thenReturn(ZIO.fail(GeneralServiceError("boom!")))
  when(partitioningServiceMockV2.getPartitioningAdditionalData(3L))
    .thenReturn(ZIO.fail(NotFoundServiceError("not found")))

  private val partitioningServiceMockV1Layer = ZLayer.succeed(partitioningServiceMockV1)
  private val partitioningServiceMockV2Layer = ZLayer.succeed(partitioningServiceMockV2)


  override def spec: Spec[TestEnvironment with Scope, Any] = {
    suite("PartitioningControllerSuite")(
      suite("CreatePartitioningIfNotExistsSuite")(
        test("Returns expected AtumContextDTO") {
          for {
            result <- PartitioningController.createPartitioningIfNotExists(partitioningSubmitDTO1)
          } yield assertTrue(result == atumContextDTO1)
        },
        test("Returns expected InternalServerErrorResponse") {
          assertZIO(PartitioningController.createPartitioningIfNotExists(partitioningSubmitDTO2).exit)(
            failsWithA[InternalServerErrorResponse]
          )
        }
      ),
    ).provide(
      PartitioningControllerImpl.layer,
      partitioningServiceMockV1Layer,
      partitioningServiceMockV2Layer
    )
  }
}
