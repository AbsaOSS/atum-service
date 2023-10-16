package za.co.absa.atum.model.dto

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import za.co.absa.atum.model.dto.MeasureResultDTO.TypedValue

case class MeasureResultDTO(
                             mainValue: TypedValue,
                             supportValues: Map[String, TypedValue] = Map.empty
                           )


object MeasureResultDTO {
  case class TypedValue(
                         value: String,
                         @JsonScalaEnumeration(classOf[ResultValueType]) valueType: ResultValueType.ResultValueType
                       )

  class ResultValueType extends TypeReference[ResultValueType.type]

  object ResultValueType extends Enumeration {
    type ResultValueType = Value
    val String, Long, BigDecimal, Double = Value
  }

}
