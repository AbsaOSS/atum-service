package za.co.absa.atum.agent.model

import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.{DecimalType, LongType, StringType}
import org.apache.spark.sql.{Column, DataFrame}
import za.co.absa.atum.agent.core.MeasurementProcessor
import za.co.absa.atum.agent.core.MeasurementProcessor.MeasurementFunction
import za.co.absa.spark.commons.implicits.StructTypeImplicits.StructTypeEnhancements

/**
 *  Type of different measures to be applied to the columns.
 */
trait Measurement extends MeasurementProcessor {

  val controlCol: String
}

object Measurement {

  private val valueColumnName: String = "value"

  case class RecordCount(
    controlCol: String
  ) extends Measurement {

    override def function: MeasurementFunction =
      (ds: DataFrame) => ds.select(col(controlCol)).count().toString

  }

  case class DistinctRecordCount(
    controlCol: String
  ) extends Measurement {

    override def function: MeasurementFunction =
      (ds: DataFrame) => ds.select(col(controlCol)).distinct().count().toString
  }
  case class SumOfValuesOfColumn(
    controlCol: String
  ) extends Measurement {

    override def function: MeasurementFunction = (ds: DataFrame) => {
      val aggCol = sum(col(valueColumnName))
      aggregateColumn(ds, controlCol, aggCol)
    }

  }

  case class AbsSumOfValuesOfColumn(
    controlCol: String
  ) extends Measurement {

    override def function: MeasurementFunction = (ds: DataFrame) => {
      val aggCol = sum(abs(col(valueColumnName)))
      aggregateColumn(ds, controlCol, aggCol)
    }
  }

  case class SumOfHashesOfColumn(
    controlCol: String
  ) extends Measurement {

    override def function: MeasurementFunction = (ds: DataFrame) => {

      val aggregatedColumnName = ds.schema.getClosestUniqueName("sum_of_hashes")
      val value = ds
        .withColumn(aggregatedColumnName, crc32(col(controlCol).cast("String")))
        .agg(sum(col(aggregatedColumnName)))
        .collect()(0)(0)
      if (value == null) "" else value.toString
    }
  }

  private def aggregateColumn(
    ds: DataFrame,
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
