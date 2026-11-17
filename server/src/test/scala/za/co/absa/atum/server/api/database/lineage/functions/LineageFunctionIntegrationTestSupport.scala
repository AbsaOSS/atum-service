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

package za.co.absa.atum.server.api.database.lineage.functions

import za.co.absa.atum.model.dto.{PartitionDTO, PartitioningSubmitV2DTO}
import za.co.absa.atum.server.api.database.runs.functions.CreatePartitioningWithParents
import zio.ZIO
import zio.interop.catz.asyncInstance

/**
 * Shared fixture builders for server-side lineage DB function integration specs.
 */
private[functions] trait LineageFunctionIntegrationTestSupport {

  protected def partitioningKeyName: String

  protected def uniquePartitioning(tag: String): Seq[PartitionDTO] =
    Seq(PartitionDTO(partitioningKeyName, s"$tag-${java.util.UUID.randomUUID()}"))

  protected def create(
    tag: String,
    parents: Seq[Long],
    author: String = "author"
  ): ZIO[CreatePartitioningWithParents, Throwable, Long] =
    ZIO
      .serviceWithZIO[CreatePartitioningWithParents](
        _(PartitioningSubmitV2DTO(uniquePartitioning(tag), parents, author))
      )
      .map(_.toOption.get.data.get.id)
}
