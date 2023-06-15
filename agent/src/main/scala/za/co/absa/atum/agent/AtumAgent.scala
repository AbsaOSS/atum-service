package za.co.absa.atum.agent

import za.co.absa.atum.agent.model.Measurement

/**
 *  Place holder for the agent that communicate with the API.
 */
trait AtumAgent {

  def measurePublish(measure: Measurement): Unit
}

class AtumAgentImpl extends AtumAgent {
  override def measurePublish(measure: Measurement): Unit =
    println("Enqueued measurement: " + (measure))

}

object AtumAgentImpl {

  def agent = new AtumAgentImpl
}
