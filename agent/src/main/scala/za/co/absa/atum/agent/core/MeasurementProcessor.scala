package za.co.absa.atum.agent.core

import org.apache.spark.sql.DataFrame
import za.co.absa.atum.agent.core.MeasurementProcessor.MeasurementFunction

trait MeasurementProcessor {

  def measurementFunction: MeasurementFunction

}

object MeasurementProcessor {
  type MeasurementFunction = DataFrame => String
}
