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

package za.co.absa.atum.web.api.service

import org.mockito.Mockito
import org.mockito.Mockito.{times, verify, verifyNoInteractions, when}
import org.mockito.scalatest.IdiomaticMockito
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{PatienceConfiguration, ScalaFutures}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.atum.web.api.NotFoundException
import za.co.absa.atum.web.dao.ApiModelDao
import za.co.absa.atum.web.model.{Flow, Segmentation}

import java.util.UUID
import scala.concurrent.Future


class SegmentationServiceTest extends AnyFlatSpec with ScalaFutures with PatienceConfiguration
  with Matchers with IdiomaticMockito with BeforeAndAfterEach {

  override def beforeEach(): Unit = {
    Mockito.reset(mockedSegDao, mockedFlowDao) // this allows verify to always count from fresh 0 in each test
  }

  private val mockedSegDao: ApiModelDao[Segmentation] = mock[ApiModelDao[Segmentation]]
  private val mockedFlowDao: ApiModelDao[Flow] = mock[ApiModelDao[Flow]]

  private val flowService = new FlowService(mockedFlowDao)
  private val segService = new SegmentationService(flowService, mockedSegDao)

  private val segId1 = UUID.randomUUID()

  // not covering what BaseApiService(Test) has already covered
  "SegmentationService" should "add a segmentation with flow dependency check (flow exists)" in {
    val existingFlowId1 = UUID.randomUUID()

    val freshSegmentation = Segmentation(None, flowId = existingFlowId1)
    when(mockedFlowDao.getById(existingFlowId1)).thenReturn(Future.successful(Some(Flow(Some(existingFlowId1), None)))) // flow found
    when(mockedSegDao.add(freshSegmentation)).thenReturn(Future.successful(segId1))

    whenReady(segService.add(freshSegmentation)) {
      _ shouldBe segId1
    }
    verify(mockedSegDao, times(1)).add(freshSegmentation)
    verify(mockedFlowDao, times(1)).getById(existingFlowId1)
  }

  it should "prevent adding a segmentation with flow dependency check (flow does not exist)" in {
    val nonExistingFlowId1 = UUID.randomUUID()
    val freshSegmentation = Segmentation(None, flowId = nonExistingFlowId1)

    when(mockedFlowDao.getById(nonExistingFlowId1)).thenReturn(Future.successful(None)) // flow not found
    // no need to mock mockedSegDao.add(freshSegmentation) - expecting no interaction due to flow-non-exist check

    whenReady(segService.add(freshSegmentation).failed) { exception =>
      exception shouldBe a[NotFoundException]
      exception.getMessage shouldBe s"Flow referenced by id=${nonExistingFlowId1.toString} was not found."
    }
    verifyNoInteractions(mockedSegDao)
  }

}
