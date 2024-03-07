package za.co.absa.atum.agent.dispatcher

import za.co.absa.atum.model.dto.{AdditionalDataSubmitDTO, AtumContextDTO, CheckpointDTO, PartitioningDTO, PartitioningSubmitDTO}

import java.util

class Capture(maxEvents: Int) extends Dispatcher {
  import Capture._
  private val capture: Map[PartitioningDTO, Events] = Map.empty

  private val ts = new util.TreeMap[String, Events]()


  private def createPath(partitioning: PartitioningDTO): String =
    partitioning.map(p => s"${p.key}=${p.value}").mkString(start = "/", sep = "/", end = "/")
  /**
   * This method is used to ensure the server knows the given partitioning.
   * As a response the `AtumContext` is fetched from the server.
   *
   * @param partitioning  : PartitioningSubmitDTO to be used to ensure server knows the given partitioning.
   * @return AtumContextDTO.
   */
  override def createPartitioning(partitioning: PartitioningSubmitDTO): AtumContextDTO = {
    ts.put(createPath(partitioning.partitioning), Events(Nil, Map.empty))
    AtumContextDTO(partitioning = partitioning.partitioning)
  }

  /**
   * This method is used to save checkpoint to server.
   *
   * @param checkpoint : CheckpointDTO to be saved.
   */
  override def saveCheckpoint(checkpoint: CheckpointDTO): Unit = {
    val events = capture.getOrElse(checkpoint.partitioning, Events(Nil, Map.empty))
    val newEvents = events.copy(checkpoint = checkpoint :: events.checkpoint.take(maxEvents - 1))
    capture + (checkpoint.partitioning -> newEvents)
  }

  /**
   * This method is used to save the additional data to the server.
   *
   * @param additionalData the data to be saved.
   */
  override def saveAdditionalData(additionalData: AdditionalDataSubmitDTO): Unit = {
    val events = capture.getOrElse(additionalData.partitioning, Events(Nil, Map.empty))
    val newEvents = events.copy(additionalData = additionalData.additionalData)
    capture + (additionalData.partitioning -> newEvents)
  }
}

object Capture {
  def apply(maxEvents: Int): Capture = new Capture(maxEvents)

  case class Events(
    checkpoint: List[CheckpointDTO],
    additionalData: Map[String, Option[String]]
  )
}
