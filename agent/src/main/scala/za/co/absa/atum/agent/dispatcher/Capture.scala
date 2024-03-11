package za.co.absa.atum.agent.dispatcher

import za.co.absa.atum.model.dto._

import java.util

class Capture(maxEvents: Int) extends Dispatcher {
  import Capture._

  private val ts = util.Collections.synchronizedSortedMap(new util.TreeMap[String, Events]())

  private def createPath(partitioning: PartitioningDTO): String =
    partitioning.map(p => s"${p.key}=${p.value}").mkString(start = "/", sep = "/", end = "/")

  /**
   *  This method is used to ensure the server knows the given partitioning.
   *  As a response the `AtumContext` is fetched from the server.
   *
   *  @param partitioning  : PartitioningSubmitDTO to be used to ensure server knows the given partitioning.
   *  @return AtumContextDTO.
   */
  override def createPartitioning(partitioning: PartitioningSubmitDTO): AtumContextDTO = {
    ts.put(createPath(partitioning.partitioning), Events(Nil, Map.empty))
    AtumContextDTO(partitioning = partitioning.partitioning)
  }

  /**
   *  This method is used to save checkpoint to server.
   *
   *  @param checkpoint : CheckpointDTO to be saved.
   */
  override def saveCheckpoint(checkpoint: CheckpointDTO): Unit = {
    ts.compute(
      createPath(checkpoint.partitioning),
      (_, events) =>
        Option(events)
          .map(_.copy(checkpoint = checkpoint :: events.checkpoint.take(maxEvents - 1)))
          .getOrElse(Events(Nil, Map.empty))
    )
  }

  /**
   *  This method is used to save the additional data to the server.
   *
   *  @param additionalData the data to be saved.
   */
  override def saveAdditionalData(additionalData: AdditionalDataSubmitDTO): Unit = {
    ts.compute(
      createPath(additionalData.partitioning),
      (_, events) =>
        Option(events).map(_.copy(additionalData = additionalData.additionalData)).getOrElse(Events(Nil, Map.empty))
    )
  }

  def prefixIter(prefix: String): Iterator[(String, Events)] = {
    val prefixWithSlash = sanitizePrefix(prefix)
    val prefixWithSlashLength = prefixWithSlash.length
    val tailMap = ts.tailMap(prefixWithSlash)
    val it = tailMap.entrySet().iterator()
    new Iterator[(String, Events)] {
      private var _prefetched = Option.empty[Events]
      override def hasNext: Boolean = _prefetched match {
        case Some(_) => true
        case None if it.hasNext =>
          val entry = it.next()
          val key = entry.getKey
          if (key.startsWith(prefixWithSlash)) {
            _prefetched = Some(entry.getValue)
            true
          } else false
      }
      override def next(): (String, Events) = {
        val entry = it.next()
        val key = entry.getKey
        val keyWithoutPrefix = key.substring(prefixWithSlashLength)
        keyWithoutPrefix -> entry.getValue
      }
    }
  }
}

object Capture {
  def apply(maxEvents: Int): Capture = new Capture(maxEvents)

  case class Events(
    checkpoint: List[CheckpointDTO],
    additionalData: Map[String, Option[String]]
  )

  private def sanitizePrefix(prefix: String): String = {
    if (prefix == null || prefix.isEmpty) "/"
    else {
      val withLeadingSlash = if (prefix.startsWith("/")) prefix else s"/$prefix"
      if (withLeadingSlash.endsWith("/")) withLeadingSlash else s"$withLeadingSlash/"
    }
  }
}
