package za.co.absa.atum.agent.dispatcher

import com.typesafe.config.Config
import za.co.absa.atum.model.dto._

import java.util
import scala.jdk.CollectionConverters.IteratorHasAsScala

/**
 *  This dispatcher instead of sending data captures them and stores them in memory.
 *  @param checkpointsLimit maximal amount of checkpoints to be stored for each partition.
 *                   If the limit is reached, the oldest checkpoint is removed.
 *                   Value 0 removes this limit.
 */
class CapturingDispatcher(checkpointsLimit: Int) extends Dispatcher {
  import CapturingDispatcher._

  private val ts = util.Collections.synchronizedSortedMap(new util.TreeMap[String, PartitionedData]())

  /**
   *  This method is used to ensure the server knows the given partitioning.
   *  As a response the `AtumContext` is fetched from the server.
   *
   *  @param partitioning  : PartitioningSubmitDTO to be used to ensure server knows the given partitioning.
   *  @return AtumContextDTO.
   */
  override def createPartitioning(partitioning: PartitioningSubmitDTO): AtumContextDTO = {
    AtumContextDTO(partitioning = partitioning.partitioning)
  }

  /**
   *  This method is used to save checkpoint to server.
   *
   *  @param checkpoint : CheckpointDTO to be saved.
   */
  override def saveCheckpoint(checkpoint: CheckpointDTO): Unit = {
    val path = createPath(checkpoint.partitioning)
    ts.compute(
      path,
      (_, events) =>
        Option(events)
          .map(_.copy(checkpointStack = checkpoint :: takeLatestCheckpoints(events.checkpointStack)))
          .getOrElse(PartitionedData(path, checkpoint :: Nil, Map.empty))
    )
  }

  private def takeLatestCheckpoints(list: List[CheckpointDTO]): List[CheckpointDTO] = {
    if (checkpointsLimit <= 0) list
    else if (checkpointsLimit == 1) Nil
    else if (checkpointsLimit > list.size) list
    else list.take(checkpointsLimit - 1)
  }

  /**
   *  This method is used to save the additional data to the server.
   *
   *  @param additionalData the data to be saved.
   */
  override def saveAdditionalData(additionalData: AdditionalDataSubmitDTO): Unit = {
    val path = createPath(additionalData.partitioning)
    ts.compute(
      path,
      (_, events) =>
        Option(events)
          .map(_.copy(additionalData = additionalData.additionalData))
          .getOrElse(PartitionedData(path, Nil, additionalData.additionalData))
    )
  }

  /**
   *  This method is used to clear all captured data.
   */
  def clear(): Unit = ts.clear()

  /**
   *  This method creates iterator iterating over all captured data with specified prefix.
   */
  def prefixIter(prefix: String): Iterator[PartitionedData] = {
    val prefixWithSlash = sanitizeKey(prefix)
    ts.tailMap(prefixWithSlash)
      .entrySet()
      .iterator()
      .asScala
      .takeWhile(_.getKey.startsWith(prefixWithSlash))
      .map(_.getValue)
  }

  /**
   *  This method is used to get the partitioned data for the given key.
   *
   *  @param key the key of the partitioned data. This key will be wrapped in `/` if it's not already.
   *             If the key is empty or null, the root partition will be returned.
   *  @return the partitioned data
   */
  def getPartition(key: String): Option[PartitionedData] = {
    Option(ts.get(sanitizeKey(key)))
  }
}

object CapturingDispatcher {
  private val CheckpointLimitKey = "checkpoints-limit"
  def fromConfig(config: Config): CapturingDispatcher =
    new CapturingDispatcher(config.getInt(CheckpointLimitKey))

  case class PartitionedData(
    partition: String,
    checkpointStack: List[CheckpointDTO],
    additionalData: Map[String, Option[String]]
  ) {
    def checkpoints: List[CheckpointDTO] = checkpointStack.reverse
  }

  private def createPath(partitioning: PartitioningDTO): String =
    partitioning.map(p => s"${p.key}=${p.value}").mkString(start = "/", sep = "/", end = "/")

  private def sanitizeKey(prefix: String): String = {
    if (prefix == null || prefix.isEmpty) "/"
    else {
      val withLeadingSlash = if (prefix.startsWith("/")) prefix else s"/$prefix"
      if (withLeadingSlash.endsWith("/")) withLeadingSlash else s"$withLeadingSlash/"
    }
  }
}
