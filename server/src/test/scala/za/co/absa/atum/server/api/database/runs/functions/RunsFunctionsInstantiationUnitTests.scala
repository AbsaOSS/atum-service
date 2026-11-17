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

package za.co.absa.atum.server.api.database.runs.functions

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.atum.model.dto.PartitioningParentPatchDTO
import za.co.absa.atum.server.api.database.runs.Runs
import za.co.absa.atum.server.api.database.runs.functions.CreatePartitioningWithParents.DBResult
import za.co.absa.atum.server.api.database.runs.functions.UpdatePartitioningParent.UpdatePartitioningParentArgs

class RunsFunctionsInstantiationUnitTests extends AnyFlatSpec with Matchers {

  "CreatePartitioningWithParents" should "instantiate without DB and provide expected fieldsToSelect" in {
    val f = new CreatePartitioningWithParents()(Runs, null)
    f.fieldsToSelect should contain allOf (
      "status",
      "status_text",
      "id_partitioning",
      "created_by",
      "was_created"
    )
  }

  "CreatePartitioningWithParents.DBResult" should "support both populated and empty Option fields" in {
    val populated = DBResult(Some(42L), Some("author"), Some(true))
    populated.id shouldBe Some(42L)
    populated.createdBy shouldBe Some("author")
    populated.wasCreated shouldBe Some(true)

    val empty = DBResult(None, None, None)
    empty.id shouldBe None
    empty.createdBy shouldBe None
    empty.wasCreated shouldBe None
  }

  "UpdatePartitioningParent" should "instantiate without DB and provide expected fieldsToSelect" in {
    val f = new UpdatePartitioningParent()(Runs, null)
    f.fieldsToSelect should contain allOf (
      "status",
      "status_text"
    )

    val patch = PartitioningParentPatchDTO(parentPartitioningId = 10L, author = "linker")
    val args = UpdatePartitioningParentArgs(20L, patch)
    args.partitioningId shouldBe 20L
    args.partitioningParentPatchDTO shouldBe patch
  }

}
