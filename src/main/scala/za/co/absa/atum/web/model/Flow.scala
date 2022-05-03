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

package za.co.absa.atum.web.model

import java.util.UUID

case class Flow(id: Option[UUID],
                flowDefId: UUID,
                segmentation: Map[String, String] = Map(),
                metadata: FlowMetadata,
                runUniqueId: Option[String] = None,
                checkpoints: List[Checkpoint] = List.empty
               ) extends BaseApiModel {

  override def withId(uuid: UUID): Flow = copy(id = Some(uuid))

  /**
   * Validates this' segmentation keys against required ones: all of `segFields` must be used as key, any extra keys are allowed
   *
   * @param segFields
   * @return true if all items from `segFields` are found as keys of this Flow's segmentation keys; false otherwise
   */
  def validateSegmentationAgainst(segFields: Set[String]): Boolean = segFields.forall(segmentation.keySet.contains)

  /**
   * Wrapper for [[za.co.absa.atum.web.model.Flow#validateSegmentationAgainst(za.co.absa.atum.web.model.FlowDefinition)]]
   * for a FlowDefinition
   */
  def validateSegmentationAgainst(flowDefinition: FlowDefinition): Boolean = validateSegmentationAgainst(flowDefinition.requiredSegmentations)


  def missingSegmentationsOf(requiredSegFields: Set[String]): Set[String] = requiredSegFields -- segmentation.keySet
}
