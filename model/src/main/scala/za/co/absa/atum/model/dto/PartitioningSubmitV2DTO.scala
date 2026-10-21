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

case class PartitioningSubmitV2DTO(
  partitioning: PartitioningDTO,
  parentPartitioningIds: Seq[Long],
  author: String
)

object PartitioningSubmitV2DTO {

  /**
   *  Single-parent compatibility factory.
   */
  def apply(
    partitioning: PartitioningDTO,
    parentPartitioningId: Option[Long],
    author: String
  ): PartitioningSubmitV2DTO =
    new PartitioningSubmitV2DTO(partitioning, parentPartitioningId.toSeq, author)

  implicit val decodePartitioningSubmitV2DTO: Decoder[PartitioningSubmitV2DTO] = Decoder.instance { c =>
    for {
      partitioning <- c.downField("partitioning").as[PartitioningDTO]
      oldParent <- c.downField("parentPartitioningId").as[Option[Long]]
      newParents <- c.downField("parentPartitioningIds").as[Option[Seq[Long]]]
      author <- c.downField("author").as[String]
    } yield new PartitioningSubmitV2DTO(
      partitioning,
      (oldParent.toSeq ++ newParents.getOrElse(Seq.empty)).distinct,
      author
    )
  }

  implicit val encodePartitioningSubmitV2DTO: Encoder[PartitioningSubmitV2DTO] = Encoder.instance { dto =>
    Json.obj(
      "partitioning" -> dto.partitioning.asJson,
      "parentPartitioningIds" -> dto.parentPartitioningIds.asJson,
      "author" -> dto.author.asJson
    )
  }
}
