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

package za.co.absa.atum.agent

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.atum.agent.AtumContext.AtumPartitions
import za.co.absa.atum.agent.model.Measure.{RecordCount, SumOfValuesOfColumn}
import za.co.absa.atum.agent.model.Measurement

class AtumContextTest extends AnyFlatSpec with Matchers {

  "withMeasureAddedOrOverwritten" should "add a new measure if not exists, overwrite it otherwise" in {

    val atumContext = AtumAgent.getOrCreateAtumContext(AtumPartitions("foo1"->"bar"))

    assert(atumContext.currentMeasures.isEmpty)

    val atumContextWithRecordCount =
      atumContext.addMeasure(RecordCount("id"))
    assert(atumContextWithRecordCount.currentMeasures.size == 1)

    val atumContextWithTwoRecordCount =
      atumContextWithRecordCount.addMeasures(
        Set(RecordCount("id"), RecordCount("x"))
      )
    assert(atumContextWithTwoRecordCount.currentMeasures.size == 2)

    val atumContextWithTwoDistinctRecordCount =
      atumContextWithRecordCount.addMeasures(
        Set(RecordCount("id"), RecordCount("one"))
      )

    assert(atumContextWithTwoDistinctRecordCount.currentMeasures.size == 3)

  }

  "withMeasureRemoved" should "remove a measure if exists" in {

    val atumContext = AtumAgent.getOrCreateAtumContext(AtumPartitions("foo2"->"bar"))
    assert(atumContext.currentMeasures.isEmpty)

    val atumContext1 = atumContext.addMeasures(
      Set(RecordCount("id"), RecordCount("id"), RecordCount("other"))
    )
    assert(atumContext1.currentMeasures.size == 2)

    val atumContextRemoved = atumContext1.removeMeasure(RecordCount("id"))
    assert(atumContextRemoved.currentMeasures.size == 1)
    assert(atumContextRemoved.currentMeasures.head == RecordCount("other"))
  }

  "createCheckpointOnProvidedData" should "create a Checkpoint on provided data" in {
    val atumAgent = new AtumAgent
    val atumPartitions = AtumPartitions("key" -> "value")
    val atumContext = atumAgent.getOrCreateAtumContext(atumPartitions)

    val measurements = Seq(Measurement(RecordCount("col"), "1"), Measurement(SumOfValuesOfColumn("col"), 1))

    val checkpoint = atumContext.createCheckpointOnProvidedData(
      checkpointName = "name",
      author = "author",
      measurements = measurements
    )

    assert(checkpoint.name == "name")
    assert(checkpoint.author == "author")
    assert(!checkpoint.measuredByAtumAgent)
    assert(checkpoint.atumPartitions == atumPartitions)
    assert(checkpoint.processStartTime == checkpoint.processEndTime.get)
    assert(checkpoint.measurements == measurements)
  }

}
