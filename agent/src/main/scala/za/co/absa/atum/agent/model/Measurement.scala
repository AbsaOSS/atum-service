package za.co.absa.atum.agent.model

import org.apache.spark.sql.{Column, DataFrame, Dataset, Row}
import org.apache.spark.sql.functions.col
import org.apache.spark.sql.types.{DecimalType, LongType, StringType}
import za.co.absa.atum.agent.core.MeasurementProcessor
import org.apache.spark.sql.functions._
import za.co.absa.spark.commons.implicits.StructTypeImplicits.StructTypeEnhancements

/**
 *  Type of different measures to be applied to the columns.
 */
trait Measurement extends MeasurementProcessor {
  val name: String
  val controlCol: String
  val resultValue: Option[String]

  def withResult(s: Option[String]): Measurement
}

object Measurement {

  case class RecordCount(
    name: String,
    controlCol: String,
    resultValue: Option[String] = None
  ) extends Measurement {
    override def withResult(s: Option[String]): Measurement =
      this.copy(resultValue = s)
    override def getMeasureFunction: MeasurementFunction =
      (ds: Dataset[Row]) => ds.select(col(controlCol)).count().toString

  }

  case class DistinctRecordCount(
    name: String,
    controlCol: String,
    resultValue: Option[String] = None
  ) extends Measurement {
    override def withResult(s: Option[String]): Measurement =
      this.copy(resultValue = s)

    override def getMeasureFunction: MeasurementFunction =
      (ds: Dataset[Row]) => ds.select(col(controlCol)).distinct().count().toString
  }
  case class SumOfValuesOfColumn(
    name: String,
    controlCol: String,
    resultValue: Option[String] = None
  ) extends Measurement {
    override def withResult(s: Option[String]): Measurement =
      this.copy(resultValue = s)

    override def getMeasureFunction: MeasurementFunction = (ds: Dataset[Row]) => {
      val aggCol = sum(col(valueColumnName))
      aggregateColumn(ds, controlCol, aggCol)
    }

  }

  case class AbsSumOfValuesOfColumn(
    name: String,
    controlCol: String,
    resultValue: Option[String] = None
  ) extends Measurement {
    override def withResult(s: Option[String]): Measurement =
      this.copy(resultValue = s)

    override def getMeasureFunction: MeasurementFunction = (ds: Dataset[Row]) => {
      val aggCol = sum(abs(col(valueColumnName)))
      aggregateColumn(ds, controlCol, aggCol)
    }
  }

  case class SumOfHashesOfColumn(
    name: String,
    controlCol: String,
    resultValue: Option[String] = None
  ) extends Measurement {
    override def withResult(s: Option[String]): Measurement =
      this.copy(resultValue = s)

    override def getMeasureFunction: MeasurementFunction = (ds: Dataset[Row]) => {

      val aggregatedColumnName = ds.schema.getClosestUniqueName("sum_of_hashes")
      val value = ds
        .withColumn(aggregatedColumnName, crc32(col(controlCol).cast("String")))
        .agg(sum(col(aggregatedColumnName)))
        .collect()(0)(0)
      if (value == null) "" else value.toString
    }
  }

  private val valueColumnName: String = "value"
  private def aggregateColumn(
    ds: Dataset[Row],
    measureColumn: String,
    aggExpression: Column
  ): String = {
    val dataType = ds.select(measureColumn).schema.fields(0).dataType
    val aggregatedValue = dataType match {
      case _: LongType =>
        // This is protection against long overflow, e.g. Long.MaxValue = 9223372036854775807:
        //   scala> sc.parallelize(List(Long.MaxValue, 1)).toDF.agg(sum("value")).take(1)(0)(0)
        //   res11: Any = -9223372036854775808
        // Converting to BigDecimal fixes the issue
        // val ds2 = ds.select(col(measurement.controlCol).cast(DecimalType(38, 0)).as("value"))
        // ds2.agg(sum(abs($"value"))).collect()(0)(0)
        val ds2 = ds.select(
          col(measureColumn).cast(DecimalType(38, 0)).as(valueColumnName)
        )
        val collected = ds2.agg(aggExpression).collect()(0)(0)
        if (collected == null) 0 else collected
      case _: StringType =>
        // Support for string type aggregation
        val ds2 = ds.select(
          col(measureColumn).cast(DecimalType(38, 18)).as(valueColumnName)
        )
        val collected = ds2.agg(aggExpression).collect()(0)(0)
        val value =
          if (collected == null) new java.math.BigDecimal(0)
          else collected.asInstanceOf[java.math.BigDecimal]
        value.stripTrailingZeros // removes trailing zeros (2001.500000 -> 2001.5, but can introduce scientific notation (600.000 -> 6E+2)
          .toPlainString // converts to normal string (6E+2 -> "600")
      case _ =>
        val ds2 = ds.select(col(measureColumn).as(valueColumnName))
        val collected = ds2.agg(aggExpression).collect()(0)(0)
        if (collected == null) 0 else collected
    }
    // check if total is required to be presented as larger type - big decimal
    workaroundBigDecimalIssues(aggregatedValue)
  }

  private def workaroundBigDecimalIssues(value: Any): String =
    // If aggregated value is java.math.BigDecimal, convert it to scala.math.BigDecimal
    value match {
      case v: java.math.BigDecimal =>
        // Convert the value to string to workaround different serializers generate different JSONs for BigDecimal
        v.stripTrailingZeros // removes trailing zeros (2001.500000 -> 2001.5, but can introduce scientific notation (600.000 -> 6E+2)
          .toPlainString // converts to normal string (6E+2 -> "600")
      case v: BigDecimal =>
        // Convert the value to string to workaround different serializers generate different JSONs for BigDecimal
        new java.math.BigDecimal(
          v.toString()
        ).stripTrailingZeros // removes trailing zeros (2001.500000 -> 2001.5, but can introduce scientific notation (600.000 -> 6E+2)
          .toPlainString // converts to normal string (6E+2 -> "600")
      case a => a.toString
    }

}
