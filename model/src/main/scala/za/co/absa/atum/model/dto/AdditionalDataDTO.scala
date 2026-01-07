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

package za.co.absa.atum.model.dto

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class AdditionalDataDTO(
  data: AdditionalDataDTO.Data
)

object AdditionalDataDTO {
  type Data = Seq[(String, Option[AdditionalDataItemDTO])]

  implicit val encodeAdditionalDataDTO: Encoder[AdditionalDataDTO] = deriveEncoder
  implicit val decodeAdditionalDataDTO: Decoder[AdditionalDataDTO] = deriveDecoder
}

case class AdditionalDataItemV2DTO(
  key: String,
  value: Option[String],
  author: String
)

object AdditionalDataItemV2DTO {
  implicit val encodeAdditionalDataItemV2DTO: Encoder[AdditionalDataItemV2DTO] = deriveEncoder
  implicit val decodeAdditionalDataItemV2DTO: Decoder[AdditionalDataItemV2DTO] = deriveDecoder
}
