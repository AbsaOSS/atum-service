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

/**
 *  Request body of the v2 `POST /partitionings` endpoint.
 *
 *  @param partitioning           the partitioning to create (or to obtain, if it already exists)
 *  @param parentPartitioningIds  ids of the parent partitionings to link (0..N)
 *  @param author                 author stored if the partitioning is newly created
 *  @param copyMeasureDefinitions opt-in: copy the parents' measure definitions to a NEWLY created partitioning.
 *                                Absent or `null` in JSON means `false`, except for a legacy body (agents v0.6-v0.10)
 *                                with a non-null `parentPartitioningId`: there it means `true`, the behaviour those
 *                                agents rely on.
 */
case class PartitioningSubmitV2DTO(
  partitioning: PartitioningDTO,
  parentPartitioningIds: Seq[Long],
  author: String,
  copyMeasureDefinitions: Boolean = false
)

object PartitioningSubmitV2DTO {

  /**
   *  Single-parent compatibility factory. Has NO default arguments on purpose: Scala allows only one overloaded
   *  `apply` to define defaults, and that one is the synthetic case-class `apply` (flag defaults to `false`).
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
      legacyParent <- c.downField("parentPartitioningId").as[Option[Long]]
      parents <- c.downField("parentPartitioningIds").as[Option[Seq[Long]]]
      author <- c.downField("author").as[String]
      copyMeasureDefinitions <- c.downField("copyMeasureDefinitions").as[Option[Boolean]]
    } yield new PartitioningSubmitV2DTO(
      partitioning,
      (legacyParent.toSeq ++ parents.getOrElse(Seq.empty)).distinct,
      author,
      // legacy agents never send the flag; the current encoder always sends it
      copyMeasureDefinitions.getOrElse(legacyParent.isDefined)
    )
  }

  implicit val encodePartitioningSubmitV2DTO: Encoder[PartitioningSubmitV2DTO] = Encoder.instance { dto =>
    Json.obj(
      "partitioning" -> dto.partitioning.asJson,
      "parentPartitioningIds" -> dto.parentPartitioningIds.asJson,
      "author" -> dto.author.asJson,
      "copyMeasureDefinitions" -> dto.copyMeasureDefinitions.asJson
    )
  }
}
