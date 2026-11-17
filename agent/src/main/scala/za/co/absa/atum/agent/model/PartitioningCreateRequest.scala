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

package za.co.absa.atum.agent.model

import za.co.absa.atum.model.dto.PartitioningDTO

/**
 *  Request handed by `AtumAgent` to `Dispatcher.createPartitioning`: the partitioning to obtain/create, its parents
 *  (0..N, in order) and the author used if the partitioning is newly created.
 *
 *  Internal carrier - not a stable API; it is public only because it appears in the signature of the public
 *  `Dispatcher` class. The default of `copyMeasureDefinitions` exists so that pre-existing construction sites
 *  keep compiling unchanged.
 *
 *  @param partitioning       the partitioning to obtain/create
 *  @param parentPartitionings the parent partitionings (0..N, in order)
 *  @param authorIfNew        the author recorded if the partitioning is newly created
 *  @param copyMeasureDefinitions if true AND the partitioning is newly created, the server copies the parents'
 *                            measure definitions to it; never copies additional data; ignored for an already existing
 *                            partitioning (copying is governed solely by this flag)
 */
final case class PartitioningCreateRequest(
  partitioning: PartitioningDTO,
  parentPartitionings: Seq[PartitioningDTO],
  authorIfNew: String,
  copyMeasureDefinitions: Boolean = false
)

object PartitioningCreateRequest {

  /**
   *  Convenience factory for a single optional parent; always sends copyMeasureDefinitions = false.
   */
  def apply(
    partitioning: PartitioningDTO,
    parentPartitioning: Option[PartitioningDTO],
    authorIfNew: String
  ): PartitioningCreateRequest =
    new PartitioningCreateRequest(partitioning, parentPartitioning.toSeq, authorIfNew)
}
