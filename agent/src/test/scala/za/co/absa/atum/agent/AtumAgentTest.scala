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

import org.scalatest.funsuite.AnyFunSuiteLike
import za.co.absa.atum.agent.AtumContext.AtumPartitions

class AtumAgentTest extends AnyFunSuiteLike {

  test("AtumAgent creates AtumContext(s) as expected") {
    val atumAgent = new AtumAgent()
    val atumPartitions = AtumPartitions("abc" -> "def")
    val subPartitions = AtumPartitions("ghi", "jkl")

    val atumContext1 = atumAgent.getOrCreateAtumContext(atumPartitions, "authorTest1")
    val atumContext2 = atumAgent.getOrCreateAtumContext(atumPartitions, "authorTest1")

    // AtumAgent returns expected instance of AtumContext
    assert(atumAgent.getOrCreateAtumContext(atumPartitions, "authorTest1") == atumContext1)
    assert(atumContext1 == atumContext2)

    // AtumSubContext contains expected AtumPartitions
    val atumSubContext = atumAgent.getOrCreateAtumSubContext(subPartitions, "authorTest1")(atumContext1)
    assert(atumSubContext.atumPartitions == (atumPartitions ++ subPartitions))

    // AtumContext contains reference to expected AtumAgent
    assert(atumSubContext.agent == atumAgent)
  }

}
