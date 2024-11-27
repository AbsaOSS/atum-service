package za.co.absa.atum.model.types

import za.co.absa.atum.model.dto.{AdditionalDataDTO, PartitionDTO, PartitioningDTO}

import scala.collection.immutable.ListMap

object BasicTypes {
  type AtumPartitions = ListMap[String, String]
  type AdditionalData = Map[String, Option[String]]

  /**
   * Object contains helper methods to work with Atum partitions.
   */
  object AtumPartitions {
    def apply(elems: (String, String)): AtumPartitions = {
      ListMap(elems)
    }

    def apply(elems: List[(String, String)]): AtumPartitions = {
      ListMap(elems: _*)
    }

    def toSeqPartitionDTO(atumPartitions: AtumPartitions): PartitioningDTO = {
      atumPartitions.map { case (key, value) => PartitionDTO(key, value) }.toSeq
    }

    def fromPartitioning(partitioning: PartitioningDTO): AtumPartitions = {
      AtumPartitions(partitioning.map(partition => Tuple2(partition.key, partition.value)).toList)
    }
  }

  object AdditionalData {
    def transformAdditionalDataDTO(additionalDataDTO: AdditionalDataDTO): AdditionalData = {
      additionalDataDTO.data.map{ case (k, v) => (k, v.flatMap(_.value)) }
    }
  }
}
