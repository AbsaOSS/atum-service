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
import org.apache.spark.sql.types.{DataType, DecimalType, LongType, StringType}
import org.apache.spark.sql.{Column, DataFrame}
import za.co.absa.atum.agent.core.MeasurementProcessor
import za.co.absa.atum.agent.core.MeasurementProcessor.MeasurementFunction
import za.co.absa.atum.model.ResultValueType

/**
 *  Type of different measures to be applied to the columns.
 */
sealed trait Measure {
  val measureName: String
  def measuredColumns: Seq[String]
  val resultValueType: ResultValueType
}

trait AtumMeasure extends Measure with MeasurementProcessor {
  protected def productIterator: Iterator[Any]

  override def equals(other: Any): Boolean = other match {
    case that: AtumMeasure =>
      this.getClass == that.getClass &&
        this.productIterator.sameElements(that.productIterator)
    case _ => false
  }

  override def hashCode(): Int = {
    productIterator.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}

final case class UnknownMeasure(measureName: String, measuredColumns: Seq[String], resultValueType: ResultValueType)
    extends Measure

object AtumMeasure {

  final class RecordCount private (val measureName: String) extends AtumMeasure {
    private val columnExpression = count("*")

    override def function: MeasurementFunction =
      (ds: DataFrame) => {
        val resultValue = ds.select(columnExpression).collect()
        MeasureResult(resultValue(0).toString, resultValueType)
      }

    override def measuredColumns: Seq[String] = Seq.empty
    override val resultValueType: ResultValueType = ResultValueType.LongValue

    override protected def productIterator: Iterator[Any] = Iterator(measureName)
  }
  object RecordCount {
    private[agent] val measureName: String = "count"
    def apply(): RecordCount = new RecordCount(measureName)
  }

  final class DistinctRecordCount private (val measureName: String, val measuredCols: Seq[String]) extends AtumMeasure {
    require(measuredCols.nonEmpty, "At least one measured column has to be defined.")

    private val columnExpression = countDistinct(col(measuredCols.head), measuredCols.tail.map(col): _*)

    override def function: MeasurementFunction =
      (ds: DataFrame) => {
        val resultValue = ds.select(columnExpression).collect()
        MeasureResult(resultValue(0)(0).toString, resultValueType)
      }

    override def measuredColumns: Seq[String] = measuredCols
    override val resultValueType: ResultValueType = ResultValueType.LongValue

    override protected def productIterator: Iterator[Any] = Iterator(measureName, measuredCols)
  }
  object DistinctRecordCount {
    private[agent] val measureName: String = "distinctCount"
    def apply(measuredCols: Seq[String]): DistinctRecordCount = new DistinctRecordCount(measureName, measuredCols)
  }

  final class SumOfValuesOfColumn private (val measureName: String, val measuredCol: String) extends AtumMeasure {
    private val columnAggFn: Column => Column = column => sum(column)

    override def function: MeasurementFunction = (ds: DataFrame) => {
      val dataType = ds.select(measuredCol).schema.fields(0).dataType
      val resultValue = ds.select(columnAggFn(castForAggregation(dataType, col(measuredCol)))).collect()
      MeasureResult(handleAggregationResult(dataType, resultValue(0)(0)), resultValueType)
    }

    override def measuredColumns: Seq[String] = Seq(measuredCol)
    override val resultValueType: ResultValueType = ResultValueType.BigDecimalValue

    override protected def productIterator: Iterator[Any] = Iterator(measureName, measuredCol)
  }
  object SumOfValuesOfColumn {
    private[agent] val measureName: String = "aggregatedTotal"
    def apply(measuredCol: String): SumOfValuesOfColumn = new SumOfValuesOfColumn(measureName, measuredCol)
  }

  final class AbsSumOfValuesOfColumn private (val measureName: String, val measuredCol: String) extends AtumMeasure {
    private val columnAggFn: Column => Column = column => sum(abs(column))

    override def function: MeasurementFunction = (ds: DataFrame) => {
      val dataType = ds.select(measuredCol).schema.fields(0).dataType
      val resultValue = ds.select(columnAggFn(castForAggregation(dataType, col(measuredCol)))).collect()
      MeasureResult(handleAggregationResult(dataType, resultValue(0)(0)), resultValueType)
    }

    override def measuredColumns: Seq[String] = Seq(measuredCol)
    override val resultValueType: ResultValueType = ResultValueType.BigDecimalValue

    override protected def productIterator: Iterator[Any] = Iterator(measureName, measuredCol)
  }
  object AbsSumOfValuesOfColumn {
    private[agent] val measureName: String = "absAggregatedTotal"
    def apply(measuredCol: String): AbsSumOfValuesOfColumn = new AbsSumOfValuesOfColumn(measureName, measuredCol)
  }

  final class SumOfTruncatedValuesOfColumn private (val measureName: String, val measuredCol: String)
      extends AtumMeasure {

    private val columnAggFn: Column => Column = column => sum(when(column >= 0, floor(column)).otherwise(ceil(column)))

    override def function: MeasurementFunction = (ds: DataFrame) => {
      val dataType = ds.select(measuredCol).schema.fields(0).dataType
      val resultValue = ds.select(columnAggFn(castForAggregation(dataType, col(measuredCol)))).collect()
      MeasureResult(handleAggregationResult(dataType, resultValue(0)(0)), resultValueType)
    }

    override def measuredColumns: Seq[String] = Seq(measuredCol)
    override val resultValueType: ResultValueType = ResultValueType.LongValue

    override protected def productIterator: Iterator[Any] = Iterator(measureName, measuredCol)
  }
  object SumOfTruncatedValuesOfColumn {
    private[agent] val measureName: String = "aggregatedTruncTotal"
    def apply(measuredCol: String): SumOfTruncatedValuesOfColumn =
      new SumOfTruncatedValuesOfColumn(measureName, measuredCol)
  }

  final class AbsSumOfTruncatedValuesOfColumn private (val measureName: String, val measuredCol: String)
      extends AtumMeasure {

    private val columnAggFn: Column => Column = column =>
      sum(abs(when(column >= 0, floor(column)).otherwise(ceil(column))))

    override def function: MeasurementFunction = (ds: DataFrame) => {
      val dataType = ds.select(measuredCol).schema.fields(0).dataType
      val resultValue = ds.select(columnAggFn(castForAggregation(dataType, col(measuredCol)))).collect()
      MeasureResult(handleAggregationResult(dataType, resultValue(0)(0)), resultValueType)
    }

    override def measuredColumns: Seq[String] = Seq(measuredCol)
    override val resultValueType: ResultValueType = ResultValueType.LongValue

    override protected def productIterator: Iterator[Any] = Iterator(measureName, measuredCol)
  }
  object AbsSumOfTruncatedValuesOfColumn {
    private[agent] val measureName: String = "absAggregatedTruncTotal"
    def apply(measuredCol: String): AbsSumOfTruncatedValuesOfColumn =
      new AbsSumOfTruncatedValuesOfColumn(measureName, measuredCol)
  }

  final class SumOfHashesOfColumn private (val measureName: String, val measuredCol: String) extends AtumMeasure {
    private val columnExpression: Column = sum(crc32(col(measuredCol).cast("String")))
    override def function: MeasurementFunction = (ds: DataFrame) => {
      val resultValue = ds.select(columnExpression).collect()
      MeasureResult(Option(resultValue(0)(0)).getOrElse("").toString, resultValueType)
    }

    override def measuredColumns: Seq[String] = Seq(measuredCol)
    override val resultValueType: ResultValueType = ResultValueType.StringValue

    override protected def productIterator: Iterator[Any] = Iterator(measureName, measuredCol)
  }
  object SumOfHashesOfColumn {
    private[agent] val measureName: String = "hashCrc32"
    def apply(measuredCol: String): SumOfHashesOfColumn = new SumOfHashesOfColumn(measureName, measuredCol)
  }

  private def castForAggregation(
    dataType: DataType,
    column: Column
  ): Column = {
    dataType match {
      case _: LongType =>
        // This is protection against long overflow, e.g. Long.MaxValue = 9223372036854775807:
        //   scala> sc.parallelize(List(Long.MaxValue, 1)).toDF.agg(sum("value")).take(1)(0)(0)
        //   res11: Any = -9223372036854775808
        // Converting to BigDecimal fixes the issue
        column.cast(DecimalType(38, 0))
      case _: StringType =>
        // Support for string type aggregation
        column.cast(DecimalType(38, 18))
      case _ =>
        column
    }
  }

  private def handleAggregationResult(dataType: DataType, result: Any): String = {
    val aggregatedValue = dataType match {
      case _: LongType =>
        if (result == null) 0 else result
      case _: StringType =>
        val value =
          if (result == null) new java.math.BigDecimal(0)
          else result.asInstanceOf[java.math.BigDecimal]
        value.stripTrailingZeros // removes trailing zeros (2001.500000 -> 2001.5, but can introduce scientific notation (600.000 -> 6E+2)
          .toPlainString // converts to normal string (6E+2 -> "600")
      case _ =>
        if (result == null) 0 else result
    }

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
