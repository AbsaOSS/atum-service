package za.co.absa.atum.agent

import za.co.absa.atum.agent.model.Measurement

/**
 *  Place holder for the agent that communicate with the API.
 */
object AtumAgent {
  def measurePublish(measure: Measurement): Unit =
    println("Enqueued measurement: " + (measure))

  def publish(context: AtumContext): Unit =
    println("Enqueued measurement: " + (context.measurements.mkString))

}
