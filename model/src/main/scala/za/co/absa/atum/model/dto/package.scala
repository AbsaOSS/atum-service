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

package za.co.absa.atum.model


import io.circe.generic.semiauto._
import io.circe._

package object dto {
  type PartitioningDTO = Seq[PartitionDTO]
  type AdditionalDataDTO = Map[String, Option[String]]

  // Implicit encoders and decoders for PartitioningDTO
  implicit val decodePartitioningDTO: Decoder[PartitioningDTO] = Decoder.decodeSeq[PartitionDTO]
  implicit val encodePartitioningDTO: Encoder[PartitioningDTO] = Encoder.encodeSeq[PartitionDTO]

  // Implicit encoders and decoders for AdditionalDataDTO
  implicit val decodeAdditionalDataDTO: Decoder[AdditionalDataDTO] = Decoder.decodeMap[String, Option[String]]
  implicit val encodeAdditionalDataDTO: Encoder[AdditionalDataDTO] = Encoder.encodeMap[String, Option[String]]

}
