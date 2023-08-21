package za.co.absa.atum.agent

import za.co.absa.atum.agent.model.{AtumPartitions, MeasureResult}

/**
 *  Place holder for the agent that communicate with the API.
 */
object AtumAgent {

  /**
   *  Sends the `MeasureResult` and extra data from a given `AtumContext` to the AtumService API.
   *  @param checkpointKey
   *  @param atumContext
   *  @param measureResult
   */
  def publish(checkpointKey: String, atumContext: AtumContext, measureResult: MeasureResult): Unit = println(
    Seq(checkpointKey, atumContext, measureResult).mkString(" || ")
  )

  /**
   *  Sends a single `MeasureResult` to the AtumService API. With not AtumContext involve.
   *
   *  @param checkpointKey
   *  @param measureResult
   */
  def measurePublish(checkpointKey: String, measure: MeasureResult): Unit =
    println(s"Enqueued measurement: $checkpointKey, " + (measure))

  /**
   *  Provides an AtumContext given a `AtumPartitions` instance. Retrieves the data from Atum Service API.
   *  @param atumPartitions
   *  @return
   */
  def createAtumContext(atumPartitions: AtumPartitions): AtumContext = {

    /**
     *  TODO: This is a place holder
     */
    AtumContext()
  }

}
