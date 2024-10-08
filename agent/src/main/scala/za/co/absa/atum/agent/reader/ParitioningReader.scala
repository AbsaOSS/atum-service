package za.co.absa.atum.agent.reader

trait PartitionReader {
  def getAdditionalData: Option[Any]
  def getCheckpoints: List[String]
}
