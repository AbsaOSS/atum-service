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

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.atum.server.api.database.lineage.Lineage
import za.co.absa.atum.server.api.database.runs.Runs
import za.co.absa.atum.server.api.database.runs.functions.GetPartitioningDescendants

class LineageFunctionsInstantiationUnitTests extends AnyFlatSpec with Matchers {

  "GetDirectParents" should "instantiate without DB and provide expected fieldsToSelect" in {
    val f = new GetDirectParents()(Lineage, null)
    f.fieldsToSelect should contain allOf (
      "parent_id",
      "parent_partitioning",
      "parent_author",
      "edge_created_by",
      "edge_created_at"
    )
  }

  "GetDirectChildren" should "instantiate without DB and provide expected fieldsToSelect" in {
    val f = new GetDirectChildren()(Lineage, null)
    f.fieldsToSelect should contain allOf (
      "child_id",
      "child_partitioning",
      "child_author",
      "edge_created_by",
      "edge_created_at"
    )
  }

  "GetPartitioningDescendants" should "instantiate without DB and provide expected fieldsToSelect" in {
    val f = new GetPartitioningDescendants()(Runs, null)
    f.fieldsToSelect should contain allOf (
      "descendant_id",
      "partitioning",
      "author",
      "has_more"
    )
  }

  "GetPartitioningLineageGraph" should "instantiate without DB and provide expected fieldsToSelect" in {
    val f = new GetPartitioningLineageGraph()(Lineage, null)
    f.fieldsToSelect should contain ("lineage_graph")
  }

}
