package za.co.absa.atum.agent.model

case class MeasureResult(measurement: Measurement, result: String)

case class MeasureResultMap(results: Map[String, MeasureResult])
