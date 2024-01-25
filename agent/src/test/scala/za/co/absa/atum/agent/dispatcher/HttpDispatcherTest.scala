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

package za.co.absa.atum.agent.dispatcher


import org.scalatest.funsuite.AnyFunSuiteLike
import com.typesafe.config.ConfigFactory
import org.mockito.MockitoSugar
import za.co.absa.atum.model.dto.{AdditionalDataSubmitDTO, CheckpointDTO, PartitioningSubmitDTO}

class HttpDispatcherTest extends AnyFunSuiteLike with MockitoSugar {

  val mockConfig = ConfigFactory.parseString(
    """
      |{
      |  "url": "http://localhost:8080"
      |}
      |""".stripMargin)

  val httpDispatcher = spy(new HttpDispatcher(mockConfig))

  test("testSaveCheckpoint") {
    val checkpoint = mock[CheckpointDTO]
    httpDispatcher.saveCheckpoint(checkpoint)

    verify(httpDispatcher).saveCheckpoint(checkpoint)
  }

  test("testCreatePartitioning") {
    val partitioning = mock[PartitioningSubmitDTO]
    val result = httpDispatcher.createPartitioning(partitioning)

    verify(httpDispatcher).createPartitioning(partitioning)

    assert(result != null)
  }

  test("testSaveAdditionalData") {
    val additionalData = mock[AdditionalDataSubmitDTO]
    httpDispatcher.saveAdditionalData(additionalData)

    verify(httpDispatcher).saveAdditionalData(additionalData)
  }
}
