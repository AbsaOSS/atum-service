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

import org.scalatest.flatspec.AnyFlatSpecLike
import za.co.absa.atum.agent.model.AtumMeasure._
import za.co.absa.atum.model.dto.MeasureDTO

class MeasuresBuilderUnitTests extends AnyFlatSpecLike {

  "mapToMeasures" should "map MeasureDTO into Measure for supported measures" in {
    val supportedMeasures = Set(
      MeasureDTO("count", Seq.empty),
      MeasureDTO("distinctCount", Seq("distinctCountCol")),
      MeasureDTO("aggregatedTotal", Seq("aggregatedTotalCol")),
      MeasureDTO("absAggregatedTotal", Seq("absAggregatedTotalCol")),
      MeasureDTO("aggregatedTruncTotal", Seq("aggregatedTruncTotalCol")),
      MeasureDTO("absAggregatedTruncTotal", Seq("absAggregatedTruncTotalCol")),
      MeasureDTO("hashCrc32", Seq("hashCrc32Col"))
    )

    val expectedMeasures = Set(
      RecordCount(),
      DistinctRecordCount(Seq("distinctCountCol")),
      SumOfValuesOfColumn("aggregatedTotalCol"),
      AbsSumOfValuesOfColumn("absAggregatedTotalCol"),
      SumOfTruncatedValuesOfColumn("aggregatedTruncTotalCol"),
      AbsSumOfTruncatedValuesOfColumn("absAggregatedTruncTotalCol"),
      SumOfHashesOfColumn("hashCrc32Col")
    )

    val actualMeasures = MeasuresBuilder.mapToMeasures(supportedMeasures)

    assert(expectedMeasures == actualMeasures)
  }

  "mapToMeasures" should "ignore unsupported or unknown measure" in {
    val unsupportedMeasure = Set(
      MeasureDTO("unsupportedMeasure", Seq("col"))
    )

    assert(MeasuresBuilder.mapToMeasures(unsupportedMeasure).isEmpty)
  }

}
