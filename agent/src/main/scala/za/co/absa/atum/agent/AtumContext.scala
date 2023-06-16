package za.co.absa.atum.agent

import org.apache.spark.sql.DataFrame
import za.co.absa.atum.agent.model.Measurement

/**
 *  AtumContext: This class provides the methods to measure Spark `Dataframe`. Also allows to add/edit/remove measures.
 *  @param measurements: A sequences of measurements.
 */

case class AtumContext(measurements: Map[String, Measurement] = Map()) {

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
     *  @return
     */
    def executeMeasure(
      measure: Measurement
    ): DataFrame = {

      val result = measure.getMeasureFunction(df)
      AtumAgent.measurePublish(measure.withResult(Some(result)))
      df
    }

    def executeMeasures(measures: Iterable[Measurement]): DataFrame = {
      measures.foreach(m => executeMeasure(m))
      df
    }

    /**
     *  Set a point in the pipeline to execute calculation.
     *  @param atumContext Contains the calculations to be done and publish the result
     *  @return
     */
    def createCheckpoint(
      atumContext: AtumContext
    ): DataFrame = {
      executeMeasures(atumContext.measurements.values)
      df
    }

  }

}
