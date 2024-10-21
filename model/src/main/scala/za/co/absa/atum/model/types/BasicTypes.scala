package za.co.absa.atum.model.types

import scala.collection.immutable.ListMap

object BasicTypes {
  type AtumPartitions = ListMap[String, String]
  type AdditionalData = Map[String, Option[String]]
}
