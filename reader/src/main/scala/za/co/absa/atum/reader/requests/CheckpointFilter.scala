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

/**
 *  Filter of a checkpoints query. All its parts are optional and a checkpoint has to satisfy all the given ones.
 *
 *  Example - checkpoints of the given name, of any of the two executions:
 *  {{{
 *    CheckpointFilter(
 *      name = Some("Data written"),
 *      properties = Map("executionID" -> Set("a", "b"))
 *    )
 *  }}}
 *
 *  Note: properties with more than one value require an Atum server supporting them; older servers reject them.
 *
 *  @param name       - only checkpoints of this name
 *  @param properties - only checkpoints that, for every property name given, have that property with one of the given
 *                      values, e.g. `Map("executionID" -> Set("a", "b"))` means `executionID IN (a, b)`; every property
 *                      needs at least one value, otherwise an `IllegalArgumentException` is thrown
 */
case class CheckpointFilter(
  name: Option[String] = None,
  properties: Map[String, Set[String]] = Map.empty
) {

  require(
    properties.values.forall(_.nonEmpty),
    s"Checkpoint properties without any accepted value: ${properties.filter(_._2.isEmpty).keys.mkString(", ")}"
  )

  private[reader] def toQueryParams: Map[String, String] = {
    name.map(QueryParamNames.CheckpointName -> _).toMap ++ propertiesParam
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
