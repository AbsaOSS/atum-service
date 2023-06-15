package za.co.absa.atum.agent

import org.apache.spark.sql.DataFrame
import za.co.absa.atum.agent.core.{MeasurementProcessor, MeasurementProcessorImplementation}
import za.co.absa.atum.agent.model.Measurement

/**
 *  AtumContext: This class provides the methods to measure Spark `Dataframe`. Also allows to add/edit/remove measures.
 *  @param processor: Provide a calculation function based on the control column and the type of calculation
 *  @param measurements: A sequences of measurements.
 *  @param atumAgent: Communicates with the API the results
 */

case class AtumContext(
  processor: MeasurementProcessor = new MeasurementProcessorImplementation,
  measurements: Map[String, Measurement] = Map()
)(implicit atumAgent: AtumAgent) {

  val agent = atumAgent
  def withMeasureAddedOrOverwritten(
    measure: Measurement
  ): AtumContext =
    this.copy(measurements = measurements + (measure.name -> measure))

  def withMeasureAddedOrOverwritten(
    measures: Iterable[Measurement]
  ): AtumContext =
    this.copy(measurements = measurements ++ measures.map(m => m.name -> m))

  def withMeasureRemoved(name: String): AtumContext =
    this.copy(measurements = measurements.filterNot(_._1 == name))

}

object AtumContext {
  implicit class DatasetWrapper(df: DataFrame) {

    /**
     *  Executes the measure directly with not AtumContext.
     *  @param measure the measure to be calculated
     *  @param processor provides the functions to be applied based on the type
     *  @param atumAgent publish the result
     *  @return
     */
    def executeMeasure(
      measure: Measurement,
      processor: MeasurementProcessor = new MeasurementProcessorImplementation
    )(implicit
      atumAgent: AtumAgent
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

    /**
     *  Set a point in the pipeline to execute calculation.
     *  @param atumContext Contains the calculations to be done and publish the result
     *  @return
     */
    def setCheckpoint(
      atumContext: AtumContext
    ): DataFrame = {

      atumContext.measurements.values.foreach(
        executeMeasure(_, atumContext.processor)(atumContext.agent)
      )
      df
    }

  }

  def context(implicit atumAgent: AtumAgent): AtumContext = AtumContext()

  def context(measurement: Iterable[Measurement])(implicit
    atumAgent: AtumAgent
  ): AtumContext =
    AtumContext(measurements = measurement.map(m => m.name -> m).toMap)

  def context(measurement: Measurement)(implicit
    atumAgent: AtumAgent
  ): AtumContext =
    AtumContext(measurements = Map(measurement.name -> measurement))

}
