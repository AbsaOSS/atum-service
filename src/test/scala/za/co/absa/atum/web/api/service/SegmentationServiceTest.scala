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

import org.mockito.{ArgumentMatchersSugar, Mockito}
import org.mockito.Mockito.{times, verify, when}
import org.mockito.scalatest.IdiomaticMockito
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{PatienceConfiguration, ScalaFutures}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.atum.web.api.NotFoundException
import za.co.absa.atum.web.dao.ApiModelDao
import za.co.absa.atum.web.model.Segmentation

import java.util.UUID
import scala.concurrent.Future


class SegmentationServiceTest extends AnyFlatSpec with ScalaFutures with PatienceConfiguration
  with Matchers with IdiomaticMockito with ArgumentMatchersSugar with BeforeAndAfterEach {

  // specific implementation in place instead of Mockito's mock (=> Future[S] type hard to mock)
  val mockedFlowService = new FlowService(null) {
    override def withFlowExistsF[S](flowId: UUID)(fn: => Future[S]): Future[S] = {
      if (flowId == existingFlowId1) fn else
        Future.failed(NotFoundException(s"Flow referenced by id=$flowId was not found."))
    }
  }
  private val mockedSegDao: ApiModelDao[Segmentation] = mock[ApiModelDao[Segmentation]]
  private val segService = new SegmentationService(mockedFlowService, mockedSegDao)

  private val existingFlowId1 = UUID.randomUUID()
  private val nonExistingFlowId1 = UUID.randomUUID()
  private val segId1 = UUID.randomUUID()

  // not covering what BaseApiService(Test) has already covered
  "SegmentationService" should "add a segmentation with flow dependency check (flow exists)" in {
    val freshSegmentation = Segmentation(None, flowId = existingFlowId1) // has existing flow
    when(mockedSegDao.add(freshSegmentation)).thenReturn(Future.successful(segId1))

    whenReady(segService.add(freshSegmentation)) {
      _ shouldBe segId1
    }
    verify(mockedSegDao, times(1)).add(freshSegmentation)
  }

  it should "prevent adding a segmentation with flow dependency check (flow not exitx)" in {
    val freshSegmentation = Segmentation(None, flowId = nonExistingFlowId1)

    whenReady(segService.add(freshSegmentation).failed) { exception =>
      exception shouldBe a[NotFoundException]
      exception.getMessage shouldBe s"Flow referenced by id=${nonExistingFlowId1.toString} was not found."
    }
    verify(mockedSegDao, times(0)).add(freshSegmentation)
  }

}
