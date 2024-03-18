package za.co.absa.atum.agent.dispatcher

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.atum.agent.dispatcher.CapturingDispatcher.PartitionedData
import za.co.absa.atum.model.dto.{AdditionalDataSubmitDTO, CheckpointDTO, MeasurementDTO, PartitionDTO, PartitioningDTO}

import java.time.ZonedDateTime
import java.util.{ConcurrentModificationException, UUID}

class CapturingDispatcherTest extends AnyFlatSpec with Matchers {
  private val DefaultProcessStartTime = ZonedDateTime.parse("2024-03-11T00:00:00Z")

  "saveAdditionalData" should "capture and store additionalData" in {
    val underTest = new CapturingDispatcher(10)
    underTest.saveAdditionalData(
      AdditionalDataSubmitDTO(createPartition("k1" -> "v1", "k2" -> "v2"), Map("opt1" -> Some("v1")))
    )

    underTest.getPartition("/k1=v1/k2=v2/") shouldBe Some(
      PartitionedData("/k1=v1/k2=v2/", Nil, Map("opt1" -> Some("v1")))
    )
  }

  it should "overwrite the saved additionalDate" in {
    val underTest = new CapturingDispatcher(10)
    underTest.saveAdditionalData(
      AdditionalDataSubmitDTO(createPartition("k1" -> "v1", "k2" -> "v2"), Map("opt1" -> Some("v1")))
    )
    underTest.saveAdditionalData(
      AdditionalDataSubmitDTO(createPartition("k1" -> "v1", "k2" -> "v2"), Map("opt1" -> Some("v2")))
    )

    underTest.getPartition("/k1=v1/k2=v2/") shouldBe Some(
      PartitionedData("/k1=v1/k2=v2/", Nil, Map("opt1" -> Some("v2")))
    )
  }

  "saveCheckpoint" should "capture and store checkpoint" in {
    val underTest = new CapturingDispatcher(0)
    val checkpoint = createCheckpoint(createPartition("k1" -> "v1", "k2" -> "v2"))
    underTest.saveCheckpoint(checkpoint)

    underTest.getPartition("/k1=v1/k2=v2/") shouldBe Some(
      PartitionedData("/k1=v1/k2=v2/", List(checkpoint), Map.empty)
    )
  }

  it should "capture all checkpoints when limit is set to 0" in {
    val underTest = new CapturingDispatcher(0)
    val checkpoint1 = createCheckpoint(createPartition("k1" -> "v1", "k2" -> "v2"))
    val checkpoint2 = createCheckpoint(createPartition("k1" -> "v1", "k2" -> "v2"))
    val checkpoint3 = createCheckpoint(createPartition("k1" -> "v1", "k2" -> "v2"))
    val checkpoint4 = createCheckpoint(createPartition("k1" -> "v1", "k2" -> "v2"))
    val checkpoint5 = createCheckpoint(createPartition("k1" -> "v1", "k2" -> "v2"))
    underTest.saveCheckpoint(checkpoint1)
    underTest.saveCheckpoint(checkpoint2)
    underTest.saveCheckpoint(checkpoint3)
    underTest.saveCheckpoint(checkpoint4)
    underTest.saveCheckpoint(checkpoint5)

    underTest.getPartition("/k1=v1/k2=v2/") shouldBe Some(
      PartitionedData("/k1=v1/k2=v2/", List(checkpoint5, checkpoint4, checkpoint3, checkpoint2, checkpoint1), Map.empty)
    )
  }

  it should "store the same checkpoint multiple times" in {
    val underTest = new CapturingDispatcher(0)
    val checkpoint = createCheckpoint(createPartition("k1" -> "v1", "k2" -> "v2"))
    underTest.saveCheckpoint(checkpoint)
    underTest.saveCheckpoint(checkpoint)

    underTest.getPartition("/k1=v1/k2=v2/") shouldBe Some(
      PartitionedData("/k1=v1/k2=v2/", List(checkpoint, checkpoint), Map.empty)
    )
  }

  it should "prepend checkpoint to the same partition" in {
    val underTest = new CapturingDispatcher(10)
    val checkpoint1 = createCheckpoint(createPartition("k1" -> "v1", "k2" -> "v2"))
    val checkpoint2 = createCheckpoint(createPartition("k1" -> "v1", "k2" -> "v2"))
    underTest.saveCheckpoint(checkpoint1)
    underTest.saveCheckpoint(checkpoint2)

    underTest.getPartition("/k1=v1/k2=v2/") shouldBe Some(
      PartitionedData("/k1=v1/k2=v2/", List(checkpoint2, checkpoint1), Map.empty)
    )
  }

  it should "keep only the max number of checkpoints" in {
    val underTest = new CapturingDispatcher(2)
    val checkpoint1 = createCheckpoint(createPartition("k1" -> "v1", "k2" -> "v2"))
    val checkpoint2 = createCheckpoint(createPartition("k1" -> "v1", "k2" -> "v2"))
    val checkpoint3 = createCheckpoint(createPartition("k1" -> "v1", "k2" -> "v2"))
    underTest.saveCheckpoint(checkpoint1)
    underTest.saveCheckpoint(checkpoint2)
    underTest.saveCheckpoint(checkpoint3)

    underTest.getPartition("/k1=v1/k2=v2/") shouldBe Some(
      PartitionedData("/k1=v1/k2=v2/", List(checkpoint3, checkpoint2), Map.empty)
    )
  }

  it should "raise an exception on concurrent modification attempt" in {
    val underTest = new CapturingDispatcher(1)
    val checkpoint1 = createCheckpoint(createPartition("k1" -> "v1"))
    val checkpoint2 = createCheckpoint(createPartition("k1" -> "v2"))
    underTest.saveCheckpoint(checkpoint1)
    underTest.saveCheckpoint(checkpoint2)

    val it = underTest.prefixIter("/")

    var idx = 0;
    var collected = List.empty[String]

    assertThrows[ConcurrentModificationException] {
      while (it.hasNext) {
        if (idx == 0) {
          underTest.saveCheckpoint(createCheckpoint(createPartition("k1" -> "v3")))
        }
        idx += 1
        collected = it.next().partition :: collected
      }
    }
  }

  "clear" should "remove all persisted partitions" in {
    val underTest = new CapturingDispatcher(10)
    val checkpoint = createCheckpoint(createPartition("k1" -> "v1", "k2" -> "v2"))
    underTest.saveCheckpoint(checkpoint)
    underTest.clear()

    underTest.prefixIter("").toList shouldBe Nil
  }

  "prefixIter" should "return all partitions with the given prefix" in {
    val underTest = new CapturingDispatcher(10)
    val checkpoint1 = createCheckpoint(createPartition("k1" -> "v1"))
    val checkpoint2 = createCheckpoint(createPartition("k1" -> "v1", "k2" -> "v2"))
    val checkpoint3 = createCheckpoint(createPartition("k1" -> "w1", "k2" -> "v2"))
    underTest.saveCheckpoint(checkpoint1)
    underTest.saveCheckpoint(checkpoint2)
    underTest.saveCheckpoint(checkpoint3)

    underTest.prefixIter("/k1=v1/").toList shouldBe List(
      PartitionedData("/k1=v1/", List(checkpoint1), Map.empty),
      PartitionedData("/k1=v1/k2=v2/", List(checkpoint2), Map.empty)
    )
  }

  it should "return every partition for empty prefix" in {
    val underTest = new CapturingDispatcher(10)
    val checkpoint1 = createCheckpoint(createPartition("k1" -> "v1"))
    val checkpoint2 = createCheckpoint(createPartition("k1" -> "v1", "k2" -> "v2"))
    val checkpoint3 = createCheckpoint(createPartition("k1" -> "v2", "k2" -> "v2"))
    underTest.saveCheckpoint(checkpoint1)
    underTest.saveCheckpoint(checkpoint2)
    underTest.saveCheckpoint(checkpoint3)

    underTest.prefixIter("").toList shouldBe List(
      PartitionedData("/k1=v1/", List(checkpoint1), Map.empty),
      PartitionedData("/k1=v1/k2=v2/", List(checkpoint2), Map.empty),
      PartitionedData("/k1=v2/k2=v2/", List(checkpoint3), Map.empty)
    )
  }

  it should "return partitions ordered alphanumerically" in {
    val underTest = new CapturingDispatcher(10)
    val checkpoint1 = createCheckpoint(createPartition("k1" -> "a"))
    val checkpoint2 = createCheckpoint(createPartition("k1" -> "a", "k2" -> "A"))
    val checkpoint3 = createCheckpoint(createPartition("k1" -> "a", "k2" -> "1"))
    val checkpoint4 = createCheckpoint(createPartition("k1" -> "a", "k2" -> "a"))
    val checkpoint5 = createCheckpoint(createPartition("k2" -> "a"))
    underTest.saveCheckpoint(checkpoint1)
    underTest.saveCheckpoint(checkpoint2)
    underTest.saveCheckpoint(checkpoint3)
    underTest.saveCheckpoint(checkpoint4)
    underTest.saveCheckpoint(checkpoint5)

    underTest.prefixIter("").map(_.partition).toList shouldBe List(
      "/k1=a/",
      "/k1=a/k2=1/",
      "/k1=a/k2=A/",
      "/k1=a/k2=a/",
      "/k2=a/"
    )
  }

  "PartitionedData.checkpoints" should "retain the checkpoint save order" in {
    val underTest = new CapturingDispatcher(10)
    val checkpoint1 = createCheckpoint(createPartition("k1" -> "v1"))
    val checkpoint2 = createCheckpoint(createPartition("k1" -> "v1"))
    val checkpoint3 = createCheckpoint(createPartition("k1" -> "v1"))
    underTest.saveCheckpoint(checkpoint1)
    underTest.saveCheckpoint(checkpoint2)
    underTest.saveCheckpoint(checkpoint3)

    underTest.getPartition("/k1=v1/").map(_.checkpoints) shouldBe Some(List(checkpoint1, checkpoint2, checkpoint3))
  }

  private def createCheckpoint(partition: PartitioningDTO): CheckpointDTO =
    CheckpointDTO(
      id = UUID.randomUUID(),
      name = "name",
      author = "author",
      partitioning = partition,
      processStartTime = DefaultProcessStartTime,
      processEndTime = None,
      measurements = Set.empty
    )

  private def createPartition(kvs: (String, String)*): PartitioningDTO = {
    kvs.map { case (k, v) => PartitionDTO(k, v) }.toSeq
  }

}
