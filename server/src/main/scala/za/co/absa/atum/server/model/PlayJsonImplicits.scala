/*
 * Copyright 2021 ABSA Group Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package za.co.absa.atum.server.model

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json._
import za.co.absa.atum.model.dto.MeasureResultDTO.{ResultValueType, TypedValue}
import za.co.absa.atum.model.dto._

object PlayJsonImplicits {

  implicit val optionStringReads: Reads[Option[String]] = new Reads[Option[String]] {
    def reads(json: JsValue): JsResult[Option[String]] = json match {
      case JsNull => JsSuccess(None)
      case JsString(s) => JsSuccess(Some(s))
      case _ => JsError("Expected JsString or JsNull")
    }
  }

  implicit val optionStringWrites: Writes[Option[String]] = new Writes[Option[String]] {
    def writes(opt: Option[String]): JsValue = opt match {
      case Some(s) => JsString(s)
      case None => JsNull
    }
  }

  implicit val resultValueTypeReads: Reads[ResultValueType] = new Reads[ResultValueType] {
    override def reads(json: JsValue): JsResult[ResultValueType] = json match {
      case JsString("String") => JsSuccess(ResultValueType.String)
      case JsString("Long") => JsSuccess(ResultValueType.Long)
      case JsString("BigDecimal") => JsSuccess(ResultValueType.BigDecimal)
      case JsString("Double") => JsSuccess(ResultValueType.Double)
      case _ => JsError("Invalid ResultValueType")
    }
  }

  implicit val resultValueTypeWrites: Writes[ResultValueType] = new Writes[ResultValueType] {
    def writes(resultValueType: ResultValueType): JsValue = resultValueType match {
      case ResultValueType.String       => Json.toJson("String")
      case ResultValueType.Long         => Json.toJson("Long")
      case ResultValueType.BigDecimal   => Json.toJson("BigDecimal")
      case ResultValueType.Double       => Json.toJson("Double")
    }
  }

  implicit val readsTypedValue: Reads[MeasureResultDTO.TypedValue] = Json.reads[MeasureResultDTO.TypedValue]
  implicit val writesTypedValue: Writes[MeasureResultDTO.TypedValue] = Json.writes[MeasureResultDTO.TypedValue]

  implicit val readsMeasureResultDTO: Reads[MeasureResultDTO] = {
    ((__ \ "mainValue").read[MeasureResultDTO.TypedValue] and
      (__ \ "supportValues").readNullable[Map[String, TypedValue]].map(_.getOrElse(Map.empty))
      )(MeasureResultDTO.apply _)
  }

  implicit val writesMeasureResultDTO: Writes[MeasureResultDTO] = Json.writes[MeasureResultDTO]

  implicit val readsMeasureDTO: Reads[MeasureDTO] = Json.reads[MeasureDTO]
  implicit val writesMeasureDTO: Writes[MeasureDTO] = Json.writes[MeasureDTO]

  implicit val readsMeasurementDTO: Reads[MeasurementDTO] = Json.reads[MeasurementDTO]
  implicit val writesMeasurementDTO: Writes[MeasurementDTO] = Json.writes[MeasurementDTO]

  implicit val readsPartitionDTO: Reads[PartitionDTO] = Json.reads[PartitionDTO]
  implicit val writesPartitionDTO: Writes[PartitionDTO] = Json.writes[PartitionDTO]

  implicit val readsCheckpointDTO: Reads[CheckpointDTO] = Json.reads[CheckpointDTO]
  implicit val writesCheckpointDTO: Writes[CheckpointDTO] = Json.writes[CheckpointDTO]

  implicit val readsPartitioningSubmitDTO: Reads[PartitioningSubmitDTO] = Json.reads[PartitioningSubmitDTO]
  implicit val writesPartitioningSubmitDTO: Writes[PartitioningSubmitDTO] = Json.writes[PartitioningSubmitDTO]

  implicit val readsStringMap: Reads[Map[String, Option[String]]] = Reads.mapReads[Option[String]]
  implicit val writesStringMap: OWrites[MapWrites.Map[String, Option[String]]] =
    Writes.genericMapWrites[Option[String], MapWrites.Map]

  implicit val readsAdditionalDataSubmitDTO: Reads[AdditionalDataSubmitDTO] = Json.reads[AdditionalDataSubmitDTO]
  implicit val writesAdditionalDataSubmitDTO: Writes[AdditionalDataSubmitDTO] = Json.writes[AdditionalDataSubmitDTO]

  implicit val readsAtumContextDTO: Reads[AtumContextDTO] = Json.reads[AtumContextDTO]
  implicit val writesAtumContextDTO: Writes[AtumContextDTO] = Json.writes[AtumContextDTO]

}
