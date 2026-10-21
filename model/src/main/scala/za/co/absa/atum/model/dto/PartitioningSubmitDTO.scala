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

import io.circe._
import io.circe.syntax._

case class PartitioningSubmitDTO(
  partitioning: PartitioningDTO,
  parentPartitionings: Seq[PartitioningDTO],
  authorIfNew: String
)

object PartitioningSubmitDTO {

  /**
   *  Single-parent compatibility factory.
   */
  def apply(
    partitioning: PartitioningDTO,
    parentPartitioning: Option[PartitioningDTO],
    authorIfNew: String
  ): PartitioningSubmitDTO =
    new PartitioningSubmitDTO(partitioning, parentPartitioning.toSeq, authorIfNew)

  implicit val decodePartitioningSubmitDTO: Decoder[PartitioningSubmitDTO] = Decoder.instance { c =>
    for {
      partitioning <- c.downField("partitioning").as[PartitioningDTO]
      oldParent <- c.downField("parentPartitioning").as[Option[PartitioningDTO]]
      newParents <- c.downField("parentPartitionings").as[Option[Seq[PartitioningDTO]]]
      author <- c.downField("authorIfNew").as[String]
    } yield new PartitioningSubmitDTO(
      partitioning,
      (oldParent.toSeq ++ newParents.getOrElse(Seq.empty)).distinct,
      author
    )
  }

  implicit val encodePartitioningSubmitDTO: Encoder[PartitioningSubmitDTO] = Encoder.instance { dto =>
    Json.obj(
      "partitioning" -> dto.partitioning.asJson,
      "parentPartitionings" -> dto.parentPartitionings.asJson,
      "authorIfNew" -> dto.authorIfNew.asJson
    )
  }
}
