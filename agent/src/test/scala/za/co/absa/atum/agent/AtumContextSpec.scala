/*
 * Copyright 2021 ABSA Group Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package za.co.absa.atum.agent

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.atum.agent.model.Measurement._

class AtumContextSpec extends AnyFlatSpec with Matchers {

  "withMeasureAddedOrOverwritten" should "add a new measure if not exists, overwrite it otherwise" in {

    val atumContext = AtumContext()

    assert(atumContext.measurements.isEmpty)

    val atumContextWithRecordCount =
      atumContext.withMeasuresAdded(RecordCount("id"))
    assert(atumContextWithRecordCount.measurements.size == 1)

    val atumContextWithTwoRecordCount =
      atumContextWithRecordCount.withMeasuresAdded(
        Seq(RecordCount("id"), RecordCount("x"))
      )
    assert(atumContextWithTwoRecordCount.measurements.size == 2)

    val atumContextWithTwoDistinctRecordCount =
      atumContextWithRecordCount.withMeasuresAdded(
        Seq(RecordCount("id"), RecordCount("one"))
      )
    assert(atumContextWithTwoDistinctRecordCount.measurements.size == 2)

    val overwrittenAtumContextWithRecordCount =
      atumContextWithTwoDistinctRecordCount.withMeasuresReplaced(RecordCount("other"))
    assert(
      overwrittenAtumContextWithRecordCount.measurements.head.controlCol == "other"
    )
  }

  "withMeasureRemoved" should "remove a measure if exists" in {

    val atumContext = AtumContext()
    assert(atumContext.measurements.isEmpty)

    val atumContext1 = atumContext.withMeasuresAdded(
      Seq(RecordCount("id"), RecordCount("id"), RecordCount("other"))
    )
    assert(atumContext1.measurements.size == 2)

    val atumContextRemoved = atumContext1.withMeasureRemoved(RecordCount("id"))
    assert(atumContextRemoved.measurements.size == 1)
    assert(atumContextRemoved.measurements.head == RecordCount("other"))
  }

}
