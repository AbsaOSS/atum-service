package za.co.absa.atum.agent

import org.apache.spark.SparkContext
import org.apache.spark.sql.{Dataset, Row}
import za.co.absa.atum.agent.core.{
  MeasurePublisher,
  MeasurementProcessor,
  MeasurementProcessorImplementation
}
import za.co.absa.atum.agent.model.Measurement
import za.co.absa.atum.agent.model.SumOfValuesOfColumn
import za.co.absa.atum.agent.model.SumOfHashesOfColumn


trait AtumContext {

  val processor: MeasurementProcessor = new MeasurementProcessorImplementation
  val publisher: MeasurePublisher = new MeasurePublisher {}

  implicit class DatasetWrapper(dataset: Dataset[Row]) {

    def addMeasure(
                    measure: Measurement
                  ): Dataset[Row] = {

      val result = processor.getFunction(dataset, measure)(dataset)

      publisher.measurePublish(measure.setResult(Some(result)))
      dataset
    }

    def addMeasure(
                    measures: Iterable[Measurement]
                  ): Dataset[Row] = {
      measures.foreach(addMeasure)
      dataset
    }

  }

}




