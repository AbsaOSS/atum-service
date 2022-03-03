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
import za.co.absa.atum.web.model.Checkpoint.CheckpointStatus
import za.co.absa.atum.web.model.{Checkpoint, CheckpointUpdate, Flow, FlowDefinition, FlowMetadata, Measurement}

import java.util.UUID
import scala.concurrent.Future


class FlowServiceTest extends AnyFlatSpec with ScalaFutures with PatienceConfiguration
  with Matchers with IdiomaticMockito with BeforeAndAfterEach {

  override def beforeEach(): Unit = {
    Mockito.reset(mockedFlowDao) // this allows verify to always count from fresh 0 in each test
  }

  private val mockedFlowDao: ApiModelDao[Flow] = mock[ApiModelDao[Flow]]

  private val flowId1 = UUID.fromString("f1d00000-6b8a-4fad-81c5-d303fb805a7b")
  private val flowDefId1 = UUID.fromString("f101d000-6b8a-4fad-81c5-d303fb805a7b")


  // custom mock due to being hard to mock by-name params or lambdas in scalaMockito
  private def setupMockedFlowDefService(flowDefExists: Boolean, requiredSegmentation: Set[String] = Set.empty) = new FlowDefinitionService(null) {
    var mockCalledCnt = 0
    val existingFlowDef = FlowDefinition(Some(flowDefId1), "Test FD", requiredSegmentation)

    override def withExistingEntityF[S](id: UUID)(fn: FlowDefinition => Future[S]): Future[S] = synchronized {
      mockCalledCnt += 1
      if (flowDefExists) {
        fn(existingFlowDef)
      } else {
        Future.failed(NotFoundException(s"FlowDefinition referenced by id=$id was not found."))
      }
    }
  }

  // not covering what BaseApiService(Test) has already covered
  "FlowService" should "add: add a flow with flowDef dependency check and segmentation check both passing" in {
    val freshFlow = Flow(None, flowDefId1, segmentation = Map("reqSeg1" -> "someValue"), null)
    val mockedFlowDefService1 = setupMockedFlowDefService(flowDefExists = true, requiredSegmentation = Set("reqSeg1"))

    val flowService = new FlowService(mockedFlowDefService1, mockedFlowDao)
    when(mockedFlowDao.add(freshFlow)).thenReturn(Future.successful(flowId1)) // flow saved & gets an id assigned

    whenReady(flowService.add(freshFlow)) {
      _ shouldBe flowId1
    }
    verify(mockedFlowDao, times(1)).add(freshFlow)
    mockedFlowDefService1.mockCalledCnt shouldBe 1
  }

  it should "add: prevent adding a flow with segmentation check failing" in {
    val freshFlow = Flow(None, flowDefId1, segmentation = Map("notConformingToRequired" -> "someValue"), null)
    val mockedFlowDefService1 = setupMockedFlowDefService(flowDefExists = true, requiredSegmentation = Set("reqSeg1"))

    val flowService = new FlowService(mockedFlowDefService1, mockedFlowDao)

    whenReady(flowService.add(freshFlow).failed) { exception =>
      exception shouldBe a[IllegalArgumentException]
      exception.getMessage should include(s"missing segmentation values for fields: reqSeg1.")
    }

    verifyNoInteractions(mockedFlowDao) // mockedFlowDao.add was not called due to an error
    mockedFlowDefService1.mockCalledCnt shouldBe 1
  }

  it should "add: prevent adding a flow with referenced by flowDef not found" in {
    val freshFlow = Flow(None, flowDefId1, Map.empty, null)
    val mockedFlowDefService1 = setupMockedFlowDefService(flowDefExists = false) // flowDef will not be found and the test case will fail on that

    val flowService = new FlowService(mockedFlowDefService1, mockedFlowDao)

    whenReady(flowService.add(freshFlow).failed) { exception =>
      exception shouldBe a[NotFoundException]
      exception.getMessage shouldBe s"FlowDefinition referenced by id=${flowDefId1.toString} was not found."
    }

    verifyNoInteractions(mockedFlowDao) // mockedFlowDao.add was not called due to an error
    mockedFlowDefService1.mockCalledCnt shouldBe 1
  }

  it should "update: update a flow with flowDef dependency check and segmentation check both passing" in {
    val existingFlow = Flow(Some(flowId1), flowDefId1, segmentation = Map("reqSeg1" -> "someValue"), null)
    val flowUpdate = Flow(Some(flowId1), flowDefId1, segmentation = Map("reqSeg1" -> "someNewValue"), null)
    val mockedFlowDefService1 = setupMockedFlowDefService(flowDefExists = true)

    val flowService = new FlowService(mockedFlowDefService1, mockedFlowDao)
    when(mockedFlowDao.getById(flowId1)).thenReturn(Future.successful(Some(existingFlow)))
    when(mockedFlowDao.update(flowUpdate)).thenReturn(Future.successful(true)) // flow updated

    whenReady(flowService.update(flowUpdate))(_ shouldBe true)
    verify(mockedFlowDao, times(1)).getById(flowId1)
    verify(mockedFlowDao, times(1)).update(flowUpdate)
    mockedFlowDefService1.mockCalledCnt shouldBe 1
  }

  it should "update: prevent updating a flow with segmentation check failing" in {
    val existingFlow = Flow(Some(flowId1), flowDefId1, segmentation = Map("reqSeg1" -> "someValue"), null)
    val flowUpdate = Flow(Some(flowId1), flowDefId1, segmentation = Map("notConformingToRequired" -> "someNewValue"), null)
    val mockedFlowDefService1 = setupMockedFlowDefService(flowDefExists = true, requiredSegmentation = Set("reqSeg1"))

    val flowService = new FlowService(mockedFlowDefService1, mockedFlowDao)
    when(mockedFlowDao.getById(flowId1)).thenReturn(Future.successful(Some(existingFlow)))

    whenReady(flowService.update(flowUpdate).failed) { exception =>
      exception shouldBe a[IllegalArgumentException]
      exception.getMessage should include(s"missing segmentation values for fields: reqSeg1.")
    }

    verify(mockedFlowDao, times(1)).getById(flowId1)
    verify(mockedFlowDao, times(0)).update(any[Flow]) // update not reached due to an error
    mockedFlowDefService1.mockCalledCnt shouldBe 1
  }

  it should "update: prevent updating a flow with referenced by flowDef not found" in {
    val existingFlow = Flow(Some(flowId1), flowDefId1, segmentation = Map("reqSeg1" -> "someValue"), null)
    val flowUpdate = Flow(Some(flowId1), flowDefId1, segmentation = Map("notConformingToRequired" -> "someNewValue"), null)
    val mockedFlowDefService1 = setupMockedFlowDefService(flowDefExists = false) // flowDef will not be found and the test case will fail on that

    val flowService = new FlowService(mockedFlowDefService1, mockedFlowDao)
    when(mockedFlowDao.getById(flowId1)).thenReturn(Future.successful(Some(existingFlow)))

    whenReady(flowService.update(flowUpdate).failed) { exception =>
      exception shouldBe a[NotFoundException]
      exception.getMessage shouldBe s"FlowDefinition referenced by id=${flowDefId1.toString} was not found."
    }

    verify(mockedFlowDao, times(1)).getById(flowId1)
    verify(mockedFlowDao, times(0)).update(any[Flow]) // update not reached due to an error
    mockedFlowDefService1.mockCalledCnt shouldBe 1
  }

  it should "updateMetadata: happy path" in {
    val existingFlow = Flow(Some(flowId1), flowDefId1, segmentation = Map("reqSeg1" -> "someValue"),
      FlowMetadata("sourceApp1", "CZ", "historyType1", "data.file.name", "sourceType1", version = 1, "2022-02-22", Map("additionalInfoKey1" -> "someValue")))
    val updatedMetadata = existingFlow.metadata.copy(country = "SA", version = 2, additionalInfo = Map("additionalInfoKey2" -> "someValue2"))

    val expectedUpdatedFlow = existingFlow.copy(metadata = updatedMetadata)
    val mockedFlowDefService = mock[FlowDefinitionService]

    val flowService = new FlowService(mockedFlowDefService, mockedFlowDao)
    when(mockedFlowDao.getById(flowId1)).thenReturn(Future.successful(Some(existingFlow))) // flow existence check
    when(mockedFlowDao.update(expectedUpdatedFlow)).thenReturn(Future.successful(true)) // flow-meta updated

    whenReady(flowService.updateMetadata(flowId1, updatedMetadata))(_ shouldBe true)

    verify(mockedFlowDao, times(1)).getById(flowId1) // flow existence check
    verify(mockedFlowDao, times(1)).update(expectedUpdatedFlow)
    verifyNoInteractions(mockedFlowDefService) // flowdefs are not checked - not needed for meta update
  }

  it should "updateMetadata: failing on flow-not-found" in {
    val updatedMetadata = FlowMetadata("sourceApp1", "CZ", "historyType1", "data.file.name", "sourceType1", version = 1, "2022-02-22", Map("additionalInfoKey1" -> "someValue"))
    val mockedFlowDefService = mock[FlowDefinitionService]

    val flowService = new FlowService(mockedFlowDefService, mockedFlowDao)
    when(mockedFlowDao.getById(flowId1)).thenReturn(Future.successful(None)) // flow existence check failing

    whenReady(flowService.updateMetadata(flowId1, updatedMetadata).failed) { exception =>
      exception shouldBe a[NotFoundException]
      exception.getMessage shouldBe s"Flow referenced by id=${flowId1.toString} was not found."
    }

    verify(mockedFlowDao, times(1)).getById(flowId1) // flow existence check
    verify(mockedFlowDao, times(0)).update(any[Flow]) // update not reached due to an error
    verifyNoInteractions(mockedFlowDefService) // flowdefs are not checked - not needed for meta update
  }

  it should "getList: passing to dao with specific filter" in {
    val mockedFlowDefService = mock[FlowDefinitionService]

    val flowService = new FlowService(mockedFlowDefService, mockedFlowDao)
    val aFilter = mock[Flow => Boolean]
    val aList = List.empty[Flow]

    // just a pass-through to dao layer
    when(mockedFlowDao.getList(1, 2, aFilter)).thenReturn(Future.successful(aList))
    whenReady(flowService.getList(1, 2, aFilter)) {
      _ shouldBe aList
    }

    verify(mockedFlowDao, times(1)).getList(1, 2, aFilter)
    verifyNoInteractions(mockedFlowDefService) // flowdefs are not checked - not needed
  }

  private val cpId1 = UUID.fromString("c43c41d0-6b8a-4fad-81c5-d303fb805a7b")
  it should "addCheckpoint: happy path" in {
    val existingFlow = Flow(Some(flowId1), flowDefId1, segmentation = Map(), null, checkpoints = List(
      Checkpoint(Some(cpId1), "myCheckpoint1", Some("processingSw1"), Some("1.2.3-RC6+build.456"), null, null, null,
        order = 1, status = CheckpointStatus.Closed)
    ))
    val freshCheckpoint = Checkpoint(None, "myCheckpoint2", Some("processingSw2"), Some("1.2.4-RC1"), null, null, null,
      order = 2, status = CheckpointStatus.Open)
    val cpId2 = UUID.randomUUID()

    val expectedUpdatedFlow = existingFlow.copy(checkpoints = existingFlow.checkpoints :+ freshCheckpoint.withId(cpId2)) // flow with added CP with a new cpId assigned
    val mockedFlowDefService = mock[FlowDefinitionService]

    val flowService = new FlowService(mockedFlowDefService, mockedFlowDao) {
      override def generateRandomId(): UUID = cpId2 // control id generation for fresh CP in test
    }
    when(mockedFlowDao.getById(flowId1)).thenReturn(Future.successful(Some(existingFlow))) // flow existence check
    when(mockedFlowDao.update(expectedUpdatedFlow)).thenReturn(Future.successful(true)) // flow-cp added

    whenReady(flowService.addCheckpoint(flowId1, freshCheckpoint))(_ shouldBe cpId2)

    verify(mockedFlowDao, times(1)).getById(flowId1) // flow existence check
    verify(mockedFlowDao, times(1)).update(expectedUpdatedFlow) // dao update call
    verifyNoInteractions(mockedFlowDefService) // flowdefs are not checked - not needed for cp update
  }

  it should "addCheckpoint: invalid order" in {
    val existingFlow = Flow(Some(flowId1), flowDefId1, segmentation = Map(), null, checkpoints = List(
      Checkpoint(Some(cpId1), "myCheckpoint1", None, None, null, null, null, order = 4, status = CheckpointStatus.Closed)
    ))
    val freshCheckpoint = Checkpoint(None, "cpWithInvalidOrder", None, None, null, null, null, order = 2, status = CheckpointStatus.Open)
    val mockedFlowDefService = mock[FlowDefinitionService]

    val flowService = new FlowService(mockedFlowDefService, mockedFlowDao)
    when(mockedFlowDao.getById(flowId1)).thenReturn(Future.successful(Some(existingFlow))) // flow existence check

    whenReady(flowService.addCheckpoint(flowId1, freshCheckpoint).failed) { exception =>
      exception shouldBe a[IllegalArgumentException]
      exception.getMessage should startWith("Checkpoint order is invalid!")
      exception.getMessage should include("2 is not larger than 4")
    }

    verify(mockedFlowDao, times(1)).getById(flowId1)
    verify(mockedFlowDao, times(0)).update(any[Flow]) // update not reached due to an error
    verifyNoInteractions(mockedFlowDefService) // flowdefs are not checked - not needed for cp update
  }

  it should "addCheckpoint: failing on flow-not-found" in {
    val freshCheckpoint = Checkpoint(None, "someCp2", None, None, null, null, null, order = 2, status = CheckpointStatus.Open)
    val mockedFlowDefService = mock[FlowDefinitionService]

    val flowService = new FlowService(mockedFlowDefService, mockedFlowDao)
    when(mockedFlowDao.getById(flowId1)).thenReturn(Future.successful(None)) // flow existence check failing

    whenReady(flowService.addCheckpoint(flowId1, freshCheckpoint).failed) { exception =>
      exception shouldBe a[NotFoundException]
      exception.getMessage shouldBe s"Flow referenced by id=${flowId1.toString} was not found."
    }

    verify(mockedFlowDao, times(1)).getById(flowId1) // flow existence check
    verify(mockedFlowDao, times(0)).update(any[Flow]) // update not reached due to an error
    verifyNoInteractions(mockedFlowDefService) // flowdefs are not checked - not needed for cp update
  }

  it should "getCheckpointList: happy path" in {
    val existingFlow = Flow(Some(flowId1), flowDefId1, segmentation = Map(), null, checkpoints = List(
      Checkpoint(Some(cpId1), "myCheckpoint1", None, None, null, null, null, order = 1, status = CheckpointStatus.Closed),
      Checkpoint(Some(UUID.randomUUID()), "myCheckpoint2", None, None, null, null, null, order = 2, status = CheckpointStatus.Open)
    ))
    val mockedFlowDefService = mock[FlowDefinitionService]

    val flowService = new FlowService(mockedFlowDefService, mockedFlowDao)

    when(mockedFlowDao.getById(flowId1)).thenReturn(Future.successful(Some(existingFlow))) // flow existence check + checkpoint retrieval
    whenReady(flowService.getCheckpointList(flowId1)) {
      _ shouldBe existingFlow.checkpoints
    }

    verify(mockedFlowDao, times(1)).getById(flowId1) // flow existence check + cp data
    verifyNoInteractions(mockedFlowDefService) // flowdefs are not checked - not needed
  }

  it should "getCheckpointList: failing on flow-not-found" in {
    val mockedFlowDefService = mock[FlowDefinitionService]

    val flowService = new FlowService(mockedFlowDefService, mockedFlowDao)
    when(mockedFlowDao.getById(flowId1)).thenReturn(Future.successful(None)) // flow existence check failing

    whenReady(flowService.getCheckpointList(flowId1).failed) { exception =>
      exception shouldBe a[NotFoundException]
      exception.getMessage shouldBe s"Flow referenced by id=${flowId1.toString} was not found."
    }

    verify(mockedFlowDao, times(1)).getById(flowId1) // flow existence check
    verifyNoInteractions(mockedFlowDefService) // flowdefs are not checked - not needed
  }

  it should "getCheckpointById: happy path" in {
    val existingFlow = Flow(Some(flowId1), flowDefId1, segmentation = Map(), null, checkpoints = List(
      Checkpoint(Some(cpId1), "myCheckpoint1", None, None, null, null, null, order = 1, status = CheckpointStatus.Closed)
    ))
    val mockedFlowDefService = mock[FlowDefinitionService]

    val flowService = new FlowService(mockedFlowDefService, mockedFlowDao)

    when(mockedFlowDao.getById(flowId1)).thenReturn(Future.successful(Some(existingFlow))) // flow existence check + checkpoint retrieval
    whenReady(flowService.getCheckpointById(flowId1, cpId1)) {
      _ shouldBe existingFlow.checkpoints.head
    }

    verify(mockedFlowDao, times(1)).getById(flowId1) // flow existence check + cp data
    verifyNoInteractions(mockedFlowDefService) // flowdefs are not checked - not needed
  }

  it should "getCheckpointById: failing on failing on cp-not-found" in {
    val existingFlow = Flow(Some(flowId1), flowDefId1, segmentation = Map(), null, checkpoints = List.empty)
    val mockedFlowDefService = mock[FlowDefinitionService]

    val flowService = new FlowService(mockedFlowDefService, mockedFlowDao)
    when(mockedFlowDao.getById(flowId1)).thenReturn(Future.successful(Some(existingFlow))) // flow existence check/retrieval

    whenReady(flowService.getCheckpointById(flowId1, cpId1).failed) { exception =>
      exception shouldBe a[NotFoundException]
      exception.getMessage shouldBe s"Checkpoint referenced by id=${cpId1.toString} was not found in Flow id=${flowId1.toString}"
    }

    verify(mockedFlowDao, times(1)).getById(flowId1) // flow existence check
    verifyNoInteractions(mockedFlowDefService) // flowdefs are not checked - not needed
  }

  it should "getCheckpointById: failing on flow-not-found" in {
    val mockedFlowDefService = mock[FlowDefinitionService]

    val flowService = new FlowService(mockedFlowDefService, mockedFlowDao)
    when(mockedFlowDao.getById(flowId1)).thenReturn(Future.successful(None)) // flow existence check failing

    whenReady(flowService.getCheckpointById(flowId1, cpId1).failed) { exception =>
      exception shouldBe a[NotFoundException]
      exception.getMessage shouldBe s"Flow referenced by id=${flowId1.toString} was not found."
    }

    verify(mockedFlowDao, times(1)).getById(flowId1) // flow existence check
    verifyNoInteractions(mockedFlowDefService) // flowdefs are not checked - not needed
  }

  it should "updateCheckpoint: happy path" in {
    val existingFlow = Flow(Some(flowId1), flowDefId1, segmentation = Map(), null, checkpoints = List(
      Checkpoint(Some(cpId1), "myCheckpoint1", Some("processingSw1"), Some("1.2.3-RC6+build.456"), "01-01-2020 07:00:00",
        "01-01-2020 07:00:10", "workflow1", order = 1, status = CheckpointStatus.Open)
    ))
    val checkpointUpdate = CheckpointUpdate(Some("myCheckpoint2"), None, Some("1.2.4-RC1"), Some("01-01-2020 07:00:01"),
      None, Some("workflow2"), None, status = Some(CheckpointStatus.Closed))

    val expectedUpdatedFlow = existingFlow.copy(checkpoints = List( // just some fields updated - only those defined in the update
      Checkpoint(Some(cpId1),
        "myCheckpoint2", Some("processingSw1"), Some("1.2.4-RC1"), "01-01-2020 07:00:01", // upd, orig, upd, upd
        "01-01-2020 07:00:10", "workflow2", order = 1, status = CheckpointStatus.Closed) // orig, upd, orig, upd
    ))
    val mockedFlowDefService = mock[FlowDefinitionService]

    val flowService = new FlowService(mockedFlowDefService, mockedFlowDao)
    when(mockedFlowDao.getById(flowId1)).thenReturn(Future.successful(Some(existingFlow))) // flow existence check
    when(mockedFlowDao.update(expectedUpdatedFlow)).thenReturn(Future.successful(true)) // flow-cp update

    whenReady(flowService.updateCheckpoint(flowId1, cpId1, checkpointUpdate))(_ shouldBe true)

    verify(mockedFlowDao, times(1)).getById(flowId1) // flow existence check
    verify(mockedFlowDao, times(1)).update(expectedUpdatedFlow) // dao update call
    verifyNoInteractions(mockedFlowDefService) // flowdefs are not checked - not needed
  }

  it should "updateCheckpoint: failing on cp-not-found" in {
    val existingFlow = Flow(Some(flowId1), flowDefId1, segmentation = Map(), null, checkpoints = List())
    val checkpointUpdate = CheckpointUpdate(status = Some(CheckpointStatus.Closed))
    val mockedFlowDefService = mock[FlowDefinitionService]

    val flowService = new FlowService(mockedFlowDefService, mockedFlowDao)
    when(mockedFlowDao.getById(flowId1)).thenReturn(Future.successful(Some(existingFlow))) // flow existence check/retrieval

    whenReady(flowService.updateCheckpoint(flowId1, cpId1, checkpointUpdate).failed) { exception =>
      exception shouldBe a[NotFoundException]
      exception.getMessage shouldBe s"Checkpoint referenced by id=${cpId1.toString} was not found in Flow id=${flowId1.toString}"
    }

    verify(mockedFlowDao, times(1)).getById(flowId1) // flow existence check
    verify(mockedFlowDao, times(0)).update(any[Flow]) // update not reached due to an error
    verifyNoInteractions(mockedFlowDefService) // flowdefs are not checked - not needed
  }

  it should "updateCheckpoint: failing on flow-not-found" in {
    val checkpointUpdate = CheckpointUpdate(status = Some(CheckpointStatus.Closed))
    val mockedFlowDefService = mock[FlowDefinitionService]

    val flowService = new FlowService(mockedFlowDefService, mockedFlowDao)
    when(mockedFlowDao.getById(flowId1)).thenReturn(Future.successful(None)) // flow existence check failing

    whenReady(flowService.updateCheckpoint(flowId1, cpId1, checkpointUpdate).failed) { exception =>
      exception shouldBe a[NotFoundException]
      exception.getMessage shouldBe s"Flow referenced by id=${flowId1.toString} was not found."
    }

    verify(mockedFlowDao, times(1)).getById(flowId1) // flow existence check
    verify(mockedFlowDao, times(0)).update(any[Flow]) // update not reached due to an error
    verifyNoInteractions(mockedFlowDefService) // flowdefs are not checked - not needed
  }

  it should "getMeasurements: happy path" in {
    val existingMeasurements = List(
      Measurement("controlName1", "controlType1", "controlCol1", controlValue = 123L),
      Measurement("controlName2", "controlType2", "controlCol2", controlValue = "ABC123")
    )
    val existingFlow = Flow(Some(flowId1), flowDefId1, segmentation = Map(), null, checkpoints = List(
      Checkpoint(Some(cpId1), "myCheckpoint1", None, None, null, null, null, order = 1, status = CheckpointStatus.Closed, existingMeasurements)
    ))
    val mockedFlowDefService = mock[FlowDefinitionService]

    val flowService = new FlowService(mockedFlowDefService, mockedFlowDao)

    when(mockedFlowDao.getById(flowId1)).thenReturn(Future.successful(Some(existingFlow))) // flow existence check + checkpoint retrieval
    whenReady(flowService.getMeasurements(flowId1, cpId1)) {
      _ shouldBe existingMeasurements
    }

    verify(mockedFlowDao, times(1)).getById(flowId1) // flow existence check + cp data
    verifyNoInteractions(mockedFlowDefService) // flowdefs are not checked - not needed
  }

  it should "getMeasurements: failing on failing on cp-not-found" in {
    val existingFlow = Flow(Some(flowId1), flowDefId1, segmentation = Map(), null, checkpoints = List.empty)
    val mockedFlowDefService = mock[FlowDefinitionService]

    val flowService = new FlowService(mockedFlowDefService, mockedFlowDao)
    when(mockedFlowDao.getById(flowId1)).thenReturn(Future.successful(Some(existingFlow))) // flow existence check/retrieval

    whenReady(flowService.getMeasurements(flowId1, cpId1).failed) { exception =>
      exception shouldBe a[NotFoundException]
      exception.getMessage shouldBe s"Checkpoint referenced by id=${cpId1.toString} was not found in Flow id=${flowId1.toString}"
    }

    verify(mockedFlowDao, times(1)).getById(flowId1) // flow existence check
    verifyNoInteractions(mockedFlowDefService) // flowdefs are not checked - not needed
  }

  it should "getMeasurements: failing on flow-not-found" in {
    val mockedFlowDefService = mock[FlowDefinitionService]

    val flowService = new FlowService(mockedFlowDefService, mockedFlowDao)
    when(mockedFlowDao.getById(flowId1)).thenReturn(Future.successful(None)) // flow existence check failing

    whenReady(flowService.getMeasurements(flowId1, cpId1).failed) { exception =>
      exception shouldBe a[NotFoundException]
      exception.getMessage shouldBe s"Flow referenced by id=${flowId1.toString} was not found."
    }

    verify(mockedFlowDao, times(1)).getById(flowId1) // flow existence check
    verifyNoInteractions(mockedFlowDefService) // flowdefs are not checked - not needed
  }

  it should "addMeasurement: happy path" in {
    val existingFlow = Flow(Some(flowId1), flowDefId1, segmentation = Map(), null, checkpoints = List(
      Checkpoint(Some(cpId1), "myCheckpoint1", None, None, null, null, null, order = 1, status = CheckpointStatus.Closed, List(
        Measurement("controlName1", "controlType1", "controlCol1", controlValue = 123L)
      ))
    ))

    val freshMeasurement = Measurement("controlName2", "controlType2", "controlCol2", controlValue = "ABC123")

    val expectedUpdatedFlow = existingFlow.copy(checkpoints =
      List(existingFlow.checkpoints.head.copy(measurements =
        existingFlow.checkpoints.head.measurements :+ freshMeasurement // i.e. freshMeasurement added after existing
      ))
    )
    val mockedFlowDefService = mock[FlowDefinitionService]

    val flowService = new FlowService(mockedFlowDefService, mockedFlowDao)
    when(mockedFlowDao.getById(flowId1)).thenReturn(Future.successful(Some(existingFlow))) // flow existence check
    when(mockedFlowDao.update(expectedUpdatedFlow)).thenReturn(Future.successful(true)) // flow-cp update

    whenReady(flowService.addMeasurement(flowId1, cpId1, freshMeasurement))(_ shouldBe true)

    verify(mockedFlowDao, times(1)).getById(flowId1) // flow existence check
    verify(mockedFlowDao, times(1)).update(expectedUpdatedFlow) // dao update call
    verifyNoInteractions(mockedFlowDefService) // flowdefs are not checked - not needed
  }

  it should "addMeasurement: failing on cp-not-found" in {
    val existingFlow = Flow(Some(flowId1), flowDefId1, segmentation = Map(), null, checkpoints = List())
    val freshMeasurement = Measurement("controlName2", "controlType2", "controlCol2", controlValue = "ABC123")
    val mockedFlowDefService = mock[FlowDefinitionService]

    val flowService = new FlowService(mockedFlowDefService, mockedFlowDao)
    when(mockedFlowDao.getById(flowId1)).thenReturn(Future.successful(Some(existingFlow))) // flow existence check/retrieval

    whenReady(flowService.addMeasurement(flowId1, cpId1, freshMeasurement).failed) { exception =>
      exception shouldBe a[NotFoundException]
      exception.getMessage shouldBe s"Checkpoint referenced by id=${cpId1.toString} was not found in Flow id=${flowId1.toString}"
    }

    verify(mockedFlowDao, times(1)).getById(flowId1) // flow existence check
    verify(mockedFlowDao, times(0)).update(any[Flow]) // update not reached due to an error
    verifyNoInteractions(mockedFlowDefService) // flowdefs are not checked - not needed
  }

  it should "addMeasurement: failing on flow-not-found" in {
    val freshMeasurement = Measurement("controlName2", "controlType2", "controlCol2", controlValue = "ABC123")
    val mockedFlowDefService = mock[FlowDefinitionService]

    val flowService = new FlowService(mockedFlowDefService, mockedFlowDao)
    when(mockedFlowDao.getById(flowId1)).thenReturn(Future.successful(None)) // flow existence check failing

    whenReady(flowService.addMeasurement(flowId1, cpId1, freshMeasurement).failed) { exception =>
      exception shouldBe a[NotFoundException]
      exception.getMessage shouldBe s"Flow referenced by id=${flowId1.toString} was not found."
    }

    verify(mockedFlowDao, times(1)).getById(flowId1) // flow existence check
    verify(mockedFlowDao, times(0)).update(any[Flow]) // update not reached due to an error
    verifyNoInteractions(mockedFlowDefService) // flowdefs are not checked - not needed
  }

}
