package za.co.absa.atum.agent

import org.scalatest.funsuite.AnyFunSuiteLike
import za.co.absa.atum.agent.AtumContext.AtumPartitions

class AtumAgentTest extends AnyFunSuiteLike {

  test("AtumAgent creates AtumContext(s) as expected") {
    val atumAgent = new AtumAgent()
    val atumPartitions = AtumPartitions("abc" -> "def")
    val subPartitions = AtumPartitions("ghi", "jkl")

    val atumContext1 = atumAgent.getOrCreateAtumContext(atumPartitions)
    val atumContext2 = atumAgent.getOrCreateAtumContext(atumPartitions)

    // AtumAgent returns expected instance of AtumContext
    assert(atumAgent.getOrCreateAtumContext(atumPartitions) == atumContext1)
    assert(atumContext1 == atumContext2)

    // AtumSubContext contains expected AtumPartitions
    val atumSubContext = atumAgent.getOrCreateAtumSubContext(subPartitions)(atumContext1)
    assert(atumSubContext.atumPartitions == (atumPartitions ++ subPartitions))

    // AtumContext contains reference to expected AtumAgent
    assert(atumSubContext.agent == atumAgent)
  }

}
