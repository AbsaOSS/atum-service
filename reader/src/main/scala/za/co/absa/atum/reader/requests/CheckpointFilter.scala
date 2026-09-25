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

package za.co.absa.atum.reader.requests

import za.co.absa.atum.model.utils.JsonSyntaxExtensions._

import java.time.ZonedDateTime

/**
 *  Filter of a checkpoints query. All its parts are optional and a checkpoint has to satisfy all the given ones.
 *
 *  Example - checkpoints of two executions processed in the last two months, latest first:
 *  {{{
 *    CheckpointFilter(
 *      properties = Map("executionID" -> Set("a", "b")),
 *      from = Some(ZonedDateTime.now().minusMonths(2))
 *    )
 *  }}}
 *
 *  Note: the time window and properties with more than one value require an Atum server supporting them; older servers
 *  ignore the time window and reject multi-value properties.
 *
 *  @param name        - only checkpoints of this name
 *  @param properties  - only checkpoints that, for every property name given, have that property with one of the given
 *                       values, e.g. `Map("executionID" -> Set("a", "b"))` means `executionID IN (a, b)`
 *  @param from        - only checkpoints with process start time at or after this time (inclusive)
 *  @param to          - only checkpoints with process start time before this time (exclusive)
 *  @param latestFirst - order of the checkpoints by their process start time: latest first if `true`, earliest first if
 *                       `false`, server default (latest first) if not specified
 */
case class CheckpointFilter(
  name: Option[String] = None,
  properties: Map[String, Set[String]] = Map.empty,
  from: Option[ZonedDateTime] = None,
  to: Option[ZonedDateTime] = None,
  latestFirst: Option[Boolean] = None
) {

  private[reader] def toQueryParams: Map[String, String] = {
    name.map(QueryParamNames.CheckpointName -> _).toMap ++
      propertiesParam ++
      from.map(QueryParamNames.From -> _.toInstant.toString) ++
      to.map(QueryParamNames.To -> _.toInstant.toString) ++
      latestFirst.map(QueryParamNames.LatestFirst -> _.toString)
  }

  private def propertiesParam: Option[(String, String)] = {
    if (properties.isEmpty) {
      None
    } else if (properties.values.forall(_.size == 1)) {
      // single values are sent in the original single-value format, understood by older servers too
      val singleValues = properties.map { case (propertyName, values) => propertyName -> values.head }
      Some(QueryParamNames.CheckpointProperties -> singleValues.asBase64EncodedJsonString)
    } else {
      val multiValues = properties.map { case (propertyName, values) => propertyName -> values.toSeq.sorted }
      Some(QueryParamNames.CheckpointProperties -> multiValues.asBase64EncodedJsonString)
    }
  }

}

object CheckpointFilter {
  val empty: CheckpointFilter = CheckpointFilter()
}
