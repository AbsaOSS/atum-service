
package za.co.absa.atum.agent.core

import za.co.absa.atum.agent.model.Measurement

trait MeasurePublisher {

  def measurePublish(measure: Measurement): Unit = //todo this could be an Either

  {

    println("Enqueued measurement: " + (measure))
  }

}


