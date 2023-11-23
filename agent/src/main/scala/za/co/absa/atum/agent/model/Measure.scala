/*
 * Copyright 2021 ABSA Group Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package za.co.absa.atum.agent.model

import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.{DecimalType, LongType, StringType}
import org.apache.spark.sql.{Column, DataFrame}
import za.co.absa.atum.agent.core.MeasurementProcessor
import za.co.absa.atum.model.dto.MeasureResultDTO.ResultValueType
import za.co.absa.spark.commons.implicits.StructTypeImplicits.StructTypeEnhancements

/**
 *  This trait represents a measure that can be applied to a column.
 */
sealed trait Measure extends MeasurementProcessor with MeasureType {
  val measuredColumn: String
}

/**
 *  This trait represents a measure type that can be applied to a column.
 */
trait MeasureType {
  val measureName: String
  val resultValueType: ResultValueType.ResultValueType
}

/**
 *  This object contains all the possible measures that can be applied to a column.
 */
object Measure {

  private val valueColumnName: String = "value"

  val supportedMeasures: Seq[MeasureType] = Seq(
    RecordCount,
    DistinctRecordCount,
    SumOfValuesOfColumn,
    AbsSumOfValuesOfColumn,
    SumOfHashesOfColumn
  )
  val supportedMeasureNames: Seq[String] = supportedMeasures.map(_.measureName)

  case class RecordCount private (
    measuredColumn: String,
    measureName: String,
    resultValueType: ResultValueType.ResultValueType
  ) extends Measure {

    override def function: MeasurementProcessor.MeasurementFunction =
      (ds: DataFrame) => {
        val resultValue = ds.select(col(measuredColumn)).count().toString
        MeasureResult(resultValue, resultValueType)
      }
  }
  object RecordCount extends MeasureType {
    def apply(measuredColumn: String): RecordCount = RecordCount(measuredColumn, measureName, resultValueType)

    override val measureName: String = "count"
    override val resultValueType: ResultValueType.ResultValueType = ResultValueType.Long
  }

  case class DistinctRecordCount private (
    measuredColumn: String,
    measureName: String,
    resultValueType: ResultValueType.ResultValueType
  ) extends Measure {

    override def function: MeasurementProcessor.MeasurementFunction =
      (ds: DataFrame) => {
        val resultValue = ds.select(col(measuredColumn)).distinct().count().toString
        MeasureResult(resultValue, resultValueType)
      }
  }
  object DistinctRecordCount extends MeasureType {
    def apply(measuredColumn: String): DistinctRecordCount = {
      DistinctRecordCount(measuredColumn, measureName, resultValueType)
    }

    override val measureName: String = "distinctCount"
    override val resultValueType: ResultValueType.ResultValueType = ResultValueType.Long
  }

  case class SumOfValuesOfColumn private (
    measuredColumn: String,
    measureName: String,
    resultValueType: ResultValueType.ResultValueType
  ) extends Measure {

    override def function: MeasurementProcessor.MeasurementFunction = (ds: DataFrame) => {
      val aggCol = sum(col(valueColumnName))
      val resultValue = aggregateColumn(ds, measuredColumn, aggCol)
      MeasureResult(resultValue, resultValueType)
    }
  }
  object SumOfValuesOfColumn extends MeasureType {
    def apply(measuredColumn: String): SumOfValuesOfColumn = {
      SumOfValuesOfColumn(measuredColumn, measureName, resultValueType)
    }

    override val measureName: String = "aggregatedTotal"
    override val resultValueType: ResultValueType.ResultValueType = ResultValueType.BigDecimal
  }

  case class AbsSumOfValuesOfColumn private (
    measuredColumn: String,
    measureName: String,
    resultValueType: ResultValueType.ResultValueType
  ) extends Measure {

    override def function: MeasurementProcessor.MeasurementFunction = (ds: DataFrame) => {
      val aggCol = sum(abs(col(valueColumnName)))
      val resultValue = aggregateColumn(ds, measuredColumn, aggCol)
      MeasureResult(resultValue, resultValueType)
    }
  }
  object AbsSumOfValuesOfColumn extends MeasureType {
    def apply(measuredColumn: String): AbsSumOfValuesOfColumn = {
      AbsSumOfValuesOfColumn(measuredColumn, measureName, resultValueType)
    }

    override val measureName: String = "absAggregatedTotal"
    override val resultValueType: ResultValueType.ResultValueType = ResultValueType.Double
  }

  case class SumOfHashesOfColumn private (
    measuredColumn: String,
    measureName: String,
    resultValueType: ResultValueType.ResultValueType
  ) extends Measure {

    override def function: MeasurementProcessor.MeasurementFunction = (ds: DataFrame) => {

      val aggregatedColumnName = ds.schema.getClosestUniqueName("sum_of_hashes")
      val value = ds
        .withColumn(aggregatedColumnName, crc32(col(measuredColumn).cast("String")))
        .agg(sum(col(aggregatedColumnName)))
        .collect()(0)(0)
      val resultValue = if (value == null) "" else value.toString
      MeasureResult(resultValue, ResultValueType.String)
    }
  }
  object SumOfHashesOfColumn extends MeasureType {
    def apply(measuredColumn: String): SumOfHashesOfColumn = {
      SumOfHashesOfColumn(measuredColumn, measureName, resultValueType)
    }

    override val measureName: String = "hashCrc32"
    override val resultValueType: ResultValueType.ResultValueType = ResultValueType.String
  }

  /**
   *  This method aggregates a column of a given data frame using a given aggregation expression.
   *  The result is converted to a string.
   *
   *  @param df            A data frame
   *  @param measureColumn A column to aggregate
   *  @param aggExpression An aggregation expression
   *  @return A string representation of the aggregated value
   */
  private def aggregateColumn(
    df: DataFrame,
    measureColumn: String,
    aggExpression: Column
  ): String = {
    val dataType = df.select(measureColumn).schema.fields(0).dataType
    val aggregatedValue = dataType match {
      case _: LongType =>
        // This is protection against long overflow, e.g. Long.MaxValue = 9223372036854775807:
        //   scala> sc.parallelize(List(Long.MaxValue, 1)).toDF.agg(sum("value")).take(1)(0)(0)
        //   res11: Any = -9223372036854775808
        // Converting to BigDecimal fixes the issue
        // val ds2 = ds.select(col(measurement.measuredColumn).cast(DecimalType(38, 0)).as("value"))
        // ds2.agg(sum(abs($"value"))).collect()(0)(0)
        val ds2 = df.select(
          col(measureColumn).cast(DecimalType(38, 0)).as(valueColumnName)
        )
        val collected = ds2.agg(aggExpression).collect()(0)(0)
        if (collected == null) 0 else collected
      case _: StringType =>
        // Support for string type aggregation
        val ds2 = df.select(
          col(measureColumn).cast(DecimalType(38, 18)).as(valueColumnName)
        )
        val collected = ds2.agg(aggExpression).collect()(0)(0)
        val value =
          if (collected == null) new java.math.BigDecimal(0)
          else collected.asInstanceOf[java.math.BigDecimal]
        value.stripTrailingZeros // removes trailing zeros (2001.500000 -> 2001.5, but can introduce scientific notation (600.000 -> 6E+2)
          .toPlainString // converts to normal string (6E+2 -> "600")
      case _ =>
        val ds2 = df.select(col(measureColumn).as(valueColumnName))
        val collected = ds2.agg(aggExpression).collect()(0)(0)
        if (collected == null) 0 else collected
    }
    // check if total is required to be presented as larger type - big decimal
    workaroundBigDecimalIssues(aggregatedValue)
  }

  /**
   *  This method converts a given value to string.
   *  It is a workaround for different serializers generating different JSONs for BigDecimal.
   *  See https://stackoverflow.com/questions/61973058/json-serialization-of-bigdecimal-returns-scientific-notation
   *
   *  @param value A value to convert
   *  @return A string representation of the value
   */
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
