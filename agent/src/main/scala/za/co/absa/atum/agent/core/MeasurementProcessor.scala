package za.co.absa.atum.agent.core

import org.apache.spark.sql.DataFrame

trait MeasurementProcessor {

  type MeasurementFunction = DataFrame => String

  def getMeasureFunction: MeasurementFunction

  def f: MeasurementFunction = getMeasureFunction

}
