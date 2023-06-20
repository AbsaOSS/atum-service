package za.co.absa.atum.agent

import za.co.absa.atum.agent.model.MeasureResult

/**
 *  Place holder for the agent that communicate with the API.
 */
object AtumAgent {

  def measurePublish(checkpointKey: String, measure: MeasureResult): Unit =
    println("Enqueued measurement: " + (measure))

  def publish(checkpointKey: String, context: AtumContext, measureResult: MeasureResult): Unit = println(
    Seq(checkpointKey, context, measureResult).mkString(" || ")
  )

}
