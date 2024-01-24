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

import org.junit.runner.RunWith
import org.mockito.Mockito.{mock, when}
import za.co.absa.atum.model.dto.PartitioningSubmitDTO
import za.co.absa.atum.server.api.TestConfigProvider
import za.co.absa.atum.server.api.database.runs.functions.CreatePartitioningIfNotExists
import za.co.absa.fadb.exceptions.ErrorInDataException
import za.co.absa.fadb.status.FunctionStatus
import zio._
import zio.test._
import zio.test.junit.ZTestJUnitRunner

@RunWith(classOf[ZTestJUnitRunner])
class PartitioningRepositorySpec extends ZIOSpecDefault {

  private val partitioningSubmitDTO1 = PartitioningSubmitDTO(
    partitioning = Seq.empty,
    parentPartitioning = None,
    authorIfNew = ""
  )
  private val partitioningSubmitDTO2 = partitioningSubmitDTO1.copy(authorIfNew = "differentAuthor")

  private val createPartitioningIfNotExistsMock = mock(classOf[CreatePartitioningIfNotExists])

  when(createPartitioningIfNotExistsMock.apply(partitioningSubmitDTO1)).thenReturn(ZIO.right(()))
  when(createPartitioningIfNotExistsMock.apply(partitioningSubmitDTO2))
    .thenReturn(ZIO.left(ErrorInDataException(FunctionStatus(50, "error in data"))))

  private val createPartitioningIfNotExistsMockLayer = ZLayer.succeed(createPartitioningIfNotExistsMock)

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    suite("PartitioningRepositorySuite")(
      suite("CreatePartitioningIfNotExistsSuite")(
        test("Returns expected Right with Unit") {
          for {
            result <- PartitioningRepository.createPartitioningIfNotExists(partitioningSubmitDTO1)
          } yield assertTrue(result.isRight)
        },
        test("Returns expected Left with StatusException") {
          for {
            result <- PartitioningRepository.createPartitioningIfNotExists(partitioningSubmitDTO2)
          } yield assertTrue(result.isLeft)
        }
      )
    ).provide(PartitioningRepositoryImpl.layer, createPartitioningIfNotExistsMockLayer)

  }.provideLayer(TestConfigProvider.layer)

}
