package za.co.absa.atum.agent.reader

class ParitioningReaderImpl (partitionData: Map[String, Any], checkpoints: List[String]) extends PartitionReader {
  override def getAdditionalData: Option[Any] = {
    partitionData.get("additionalData")
  }

  override def getCheckpoints: List[String] = {
    checkpoints
  }

}
