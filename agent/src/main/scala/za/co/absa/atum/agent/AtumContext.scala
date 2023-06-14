package za.co.absa.atum.agent

import org.apache.spark.sql.DataFrame
import za.co.absa.atum.agent.core.{MeasurementProcessor, MeasurementProcessorImplementation}
import za.co.absa.atum.agent.model.Measurement

case class AtumContext(
    processor: MeasurementProcessor = new MeasurementProcessorImplementation,
    measurements: Map[String, Measurement] = Map()
)(implicit atumAgent: AtumAgent) {
  def withMeasureAddedOrOverwritten(
      measure: Measurement
  ): AtumContext = {
    this.copy(measurements = measurements + (measure.name -> measure))
  }

  def withMeasureAddedOrOverwritten(
      measures: Iterable[Measurement]
  ): AtumContext = this.copy(measurements = measurements ++ measures.map(m => m.name -> m))

  def withMeasureRemoved(name: String): AtumContext =
    this.copy(measurements = measurements.filterNot(_._1 == name))

}

object AtumContext {
  implicit class DatasetWrapper(df: DataFrame) {

    def executeMeasure(measure: Measurement, processor: MeasurementProcessor = new MeasurementProcessorImplementation)(
        implicit atumAgent: AtumAgent
    ): DataFrame = {

      val result = processor.getFunction(df, measure)(df)
      atumAgent.measurePublish(measure.withResult(Some(result)))
      df
    }

    def executeMeasures(
        measures: Iterable[Measurement],
        processor: MeasurementProcessor = new MeasurementProcessorImplementation
    )(implicit atumAgent: AtumAgent): DataFrame = {
      measures.foreach(m => executeMeasure(m, processor))
      df
    }

    def setCheckpoint(atumContext: AtumContext)(implicit atumAgent: AtumAgent): DataFrame = {
      atumContext.measurements.values.foreach(executeMeasure(_, atumContext.processor))
      df
    }

  }

  def context(implicit atumAgent: AtumAgent): AtumContext = AtumContext()

  def context(measurement: Iterable[Measurement])(implicit
      atumAgent: AtumAgent
  ): AtumContext = AtumContext(measurements = measurement.map(m => m.name -> m).toMap)

  def context(measurement: Measurement)(implicit
      atumAgent: AtumAgent
  ): AtumContext = AtumContext(measurements = Map(measurement.name -> measurement))

}
