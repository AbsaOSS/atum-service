package za.co.absa.atum.agent.dispatcher

import org.mockito.MockitoSugar
import org.scalatest.funsuite.AnyFunSuite
import za.co.absa.atum.agent.AtumContext.AtumPartitions
import za.co.absa.atum.model.dto.{AdditionalDataDTO, AdditionalDataSubmitDTO, AtumContextDTO, CheckpointDTO, PartitionDTO, PartitioningDTO, PartitioningSubmitDTO}

//class ConsoleDispatcherTest extends AnyFunSuite with MockitoSugar {
//
//  test("createPartitioning should return AtumContextDTO") {
//    val dispatcher = new ConsoleDispatcher()
//    val atumPartitions =  AtumPartitions("abc" -> "def")
//    val partitioningDTO = AtumPartitions.toSeqPartitionDTO(atumPartitions)
//    val partitioning = PartitioningSubmitDTO(partitioningDTO, None, "testAuthor")
//    val result = dispatcher.createPartitioning(partitioning)
//    assert(result.isInstanceOf[AtumContextDTO])
//    assert(result.partitioning == partitioningDTO)
//  }

//  test("saveCheckpoint should not throw exception") {
//    val dispatcher = new ConsoleDispatcher()
//    val checkpoint = mock[CheckpointDTO]
//    assert(dispatcher.saveCheckpoint(checkpoint) == ())
//  }
//
//  test("saveAdditionalData should not throw exception") {
//    val dispatcher = new ConsoleDispatcher()
//    val additionalData = mock[AdditionalDataSubmitDTO]
//    assert(dispatcher.saveAdditionalData(additionalData) == ())
//  }

//  test("saveCheckpoint should not throw exception") {
//    val dispatcher = new ConsoleDispatcher()
//    val checkpoint = mock[CheckpointDTO]
//    try {
//      dispatcher.saveCheckpoint(checkpoint)
//    } catch {
//      case e: Exception => fail("saveCheckpoint threw an exception", e)
//    }
//  }
//
//  test("saveAdditionalData should not throw exception") {
//    val dispatcher = new ConsoleDispatcher()
//    val additionalData = mock[AdditionalDataSubmitDTO]
//    try {
//      dispatcher.saveAdditionalData(additionalData)
//    } catch {
//      case e: Exception => fail("saveAdditionalData threw an exception", e)
//    }
//  }
//
//}

class ConsoleDispatcherTest extends AnyFunSuite with MockitoSugar {

//  private[agent] def toSeqPartitionDTO(atumPartitions: AtumPartitions): PartitioningDTO = {
//    atumPartitions.map { case (key, value) => PartitionDTO(key, value) }.toSeq
//  }

//  test("createPartitioning should return AtumContextDTO") {
//    val dispatcher = new ConsoleDispatcher()
//    val atumPartitions =  AtumPartitions("abc" -> "def")
//    val partitioningDTO = AtumPartitions.toSeqPartitionDTO(atumPartitions)
//    val partitioning = PartitioningSubmitDTO(partitioningDTO, None, "testAuthor")
//    val result = dispatcher.createPartitioning(partitioning)
//    assert(result.isInstanceOf[AtumContextDTO])
//    assert(result.partitioning == "testPartitioning")
//  }

//  test("createPartitioning should return AtumContextDTO") {
//    val dispatcher = mock[ConsoleDispatcher]
//    val atumPartitions =  AtumPartitions("abc" -> "def")
//    val partitioningDTO = toSeqPartitionDTO(atumPartitions)
//    val partitioning = PartitioningSubmitDTO(partitioningDTO, None, "testAuthor")
//    when(dispatcher.createPartitioning(partitioning))
//      .thenReturn(AtumContextDTO(partitioningDTO, Set.empty, AdditionalDataDTO(additionalData = Map.empty)))
//
//    val result = dispatcher.createPartitioning(partitioning)
//    assert(result.isInstanceOf[AtumContextDTO])
//    assert(result.partitioning == "testPartitioning")
//  }

//  test("saveCheckpoint should not throw exception") {
//    val dispatcher = mock[ConsoleDispatcher]
//    val checkpoint = mock[CheckpointDTO]
//    try {
//      dispatcher.saveCheckpoint(checkpoint)
//    } catch {
//      case e: Exception => fail("saveCheckpoint threw an exception", e)
//    }
//  }

//  test("saveAdditionalData should not throw exception") {
//    val dispatcher = new ConsoleDispatcher()
//    val additionalData = mock[AdditionalDataSubmitDTO]
//    try {
//      dispatcher.saveAdditionalData(additionalData)
//    } catch {
//      case e: Exception => fail("saveAdditionalData threw an exception", e)
//    }
//  }
}
