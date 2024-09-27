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

package za.co.absa.atum.server.api

import io.circe.parser
import io.circe.syntax.EncoderOps
import za.co.absa.atum.model.dto.MeasureResultDTO.TypedValue
import za.co.absa.atum.model.dto._
import za.co.absa.atum.model.{ResultValueType, dto}
import za.co.absa.atum.server.api.database.flows.functions.GetFlowPartitionings.GetFlowPartitioningsResult
import za.co.absa.atum.server.model.{CheckpointFromDB, CheckpointItemFromDB, MeasureFromDB, PartitioningFromDB}

import java.time.ZonedDateTime
import java.util.{Base64, UUID}

trait TestData {

  protected val uuid1: UUID = UUID.randomUUID()
  protected val uuid2: UUID = UUID.randomUUID()

  // Partitioning DTO
  protected val partitioningDTO1: PartitioningDTO = Seq(
    PartitionDTO("key1", "val1"),
    PartitionDTO("key2", "val2")
  )
  protected val partitioningDTO2: PartitioningDTO = Seq(
    PartitionDTO("key2", "value2"),
    PartitionDTO("key3", "value3")
  )
  protected val partitioningDTO3: PartitioningDTO = Seq.empty

  // Partitioning submit DTO
  protected val partitioningSubmitDTO1: PartitioningSubmitDTO = PartitioningSubmitDTO(
    partitioning = partitioningDTO1,
    parentPartitioning = None,
    authorIfNew = ""
  )

  private val partitioningAsJson = parser
    .parse(
      """
        |{
        |  "version": 1,
        |  "keys": ["key1", "key2"],
        |  "keysToValuesMap": {
        |    "key1": "val1",
        |    "key2": "val2"
        |  }
        |}
        |""".stripMargin
    ).getOrElse(throw new Exception("Failed to parse JSON"))

  // Partitioning from the DB
  protected val partitioningFromDB1: PartitioningFromDB = PartitioningFromDB(
    id = 1111L,
    partitioning = partitioningAsJson,
    author = "author"
  )

  protected val getFlowPartitioningsResult1: GetFlowPartitioningsResult = GetFlowPartitioningsResult(
    id = 1111L,
    partitioningJson = partitioningAsJson,
    author = "author",
    hasMore = false
  )

  protected val getFlowPartitioningsResult2: GetFlowPartitioningsResult = GetFlowPartitioningsResult(
    id = 1111L,
    partitioningJson = partitioningAsJson,
    author = "author",
    hasMore = true
  )

  // Partitioning with ID DTO
  protected val partitioningWithIdDTO1: PartitioningWithIdDTO = PartitioningWithIdDTO(
    id = partitioningFromDB1.id,
    partitioning = partitioningDTO1,
    author = partitioningFromDB1.author
  )

  // PartitioningSubmitDTO with different author
  protected val partitioningSubmitDTO2: PartitioningSubmitDTO =
    partitioningSubmitDTO1.copy(authorIfNew = "differentAuthor")

  protected val partitioningSubmitDTO3: PartitioningSubmitDTO =
    partitioningSubmitDTO1.copy(authorIfNew = "yetAnotherAuthor")

  protected val partitioningSubmitV2DTO1: PartitioningSubmitV2DTO = PartitioningSubmitV2DTO(
    partitioning = partitioningDTO1,
    parentPartitioningId = None,
    author = ""
  )

  protected val partitioningSubmitV2DTO2: PartitioningSubmitV2DTO =
    partitioningSubmitV2DTO1.copy(author = "differentAuthor")

  protected val partitioningSubmitV2DTO3: PartitioningSubmitV2DTO =
    partitioningSubmitV2DTO1.copy(author = "yetAnotherAuthor")

  // Flow
  protected val flowDTO1: FlowDTO = FlowDTO(
    id = 1L,
    name = "FlowDTO1",
    description = Some("Test FlowDTO1"),
    fromPattern = false
  )
  protected val flowDTO2: FlowDTO = FlowDTO(
    id = 2L,
    name = "FlowDTO2",
    description = Some("Test FlowDTO2"),
    fromPattern = false
  )

  // Measure
  protected val measureDTO1: MeasureDTO = MeasureDTO("count1", Seq("col_A1", "col_B1"))
  protected val measureDTO2: MeasureDTO = MeasureDTO("count2", Seq("col_A2", "col_B2"))

  // Measure from DB
  protected val measureFromDB1: MeasureFromDB = MeasureFromDB(Some("count1"), Some(Seq("col_A1", "col_B1")))
  protected val measureFromDB2: MeasureFromDB = MeasureFromDB(Some("count2"), Some(Seq("col_A2", "col_B2")))

  // Initial Additional Data
  protected val initialAdditionalDataDTO1: InitialAdditionalDataDTO = Map(
    "key1" -> Some("value1"),
    "key2" -> None,
    "key3" -> Some("value3")
  )
  protected val initialAdditionalDataDTO2: InitialAdditionalDataDTO = Map(
    "key1" -> Some("value1"),
    "key2" -> Some("value2"),
    "key3" -> Some("value3")
  )
  protected val initialAdditionalDataDTO3: InitialAdditionalDataDTO = Map.empty

  protected val additionalDataDTO1: AdditionalDataDTO = AdditionalDataDTO(
    Map(
      "key1" -> Some(AdditionalDataItemDTO(Some("value1"), "author")),
      "key2" -> None,
      "key3" -> Some(AdditionalDataItemDTO(Some("value3"), "author"))
    )
  )

  protected val additionalDataDTO2: AdditionalDataDTO = AdditionalDataDTO(
    Map(
      "key1" -> Some(AdditionalDataItemDTO(Some("value1"), "author")),
      "key2" -> Some(AdditionalDataItemDTO(Some("value2"), "author")),
      "key3" -> Some(AdditionalDataItemDTO(Some("value3"), "author"))
    )
  )

  protected val additionalDataDTO3: AdditionalDataDTO = AdditionalDataDTO(Map.empty)

  protected val additionalDataPatchDTO1: AdditionalDataPatchDTO = AdditionalDataPatchDTO(
    byUser = "author",
    data = Map(
      "key1" -> "value1",
      "key3" -> "value3"
    )
  )

  val mainValue: TypedValue = TypedValue(
    value = "123",
    valueType = ResultValueType.LongValue
  )

  val supportValue1: TypedValue = TypedValue(
    value = "123456789",
    valueType = ResultValueType.LongValue
  )

  val supportValue2: TypedValue = TypedValue(
    value = "12345.6789",
    valueType = ResultValueType.BigDecimalValue
  )

  // Measure Result DTO
  protected val measureResultDTO1: MeasureResultDTO = MeasureResultDTO(
    mainValue = mainValue,
    supportValues = Map(
      "key1" -> supportValue1,
      "key2" -> supportValue2
    )
  )

  protected val measureResultDTO2: MeasureResultDTO = MeasureResultDTO(
    mainValue = mainValue,
    supportValues = Map(
      "key1" -> supportValue1,
      "key2" -> supportValue2
    )
  )

  // Measurement DTO
  protected val measurementsDTO1: Seq[MeasurementDTO] = Seq(
    MeasurementDTO(measureDTO1, measureResultDTO1)
  )

  protected val measurementsDTO2: Seq[MeasurementDTO] = Seq(
    MeasurementDTO(measureDTO2, measureResultDTO2)
  )

  // Additional Data DTO as a sequence
  protected val additionalDataDTOSeq1: Seq[(String, Option[String])] = Seq(
    "key1" -> Some("value1"),
    "key2" -> None,
    "key3" -> Some("value3")
  )

  // Additional Data Submit DTO
  protected val additionalDataSubmitDTO1: AdditionalDataSubmitDTO = AdditionalDataSubmitDTO(
    partitioning = Seq.empty,
    additionalData = Map.empty,
    author = ""
  )
  protected val additionalDataSubmitDTO2: AdditionalDataSubmitDTO =
    additionalDataSubmitDTO1.copy(author = "differentADAuthor")

  protected val additionalDataSubmitDTO3: AdditionalDataSubmitDTO =
    additionalDataSubmitDTO1.copy(author = "yetAnotherADAuthor")

  // Atum Context
  protected val atumContextDTO1: AtumContextDTO = AtumContextDTO(
    partitioning = partitioningSubmitDTO1.partitioning,
    measures = Set(measureDTO1, measureDTO2),
    additionalData = Map.empty
  )

  protected val atumContextDTO2: AtumContextDTO = atumContextDTO1.copy(
    partitioning = partitioningSubmitDTO2.partitioning,
    measures = Set(MeasureDTO("count", Seq("1")))
  )

  // Checkpoint Query DTO
  protected val checkpointQueryDTO1: CheckpointQueryDTO = CheckpointQueryDTO(
    partitioning = partitioningDTO1,
    limit = Option(2),
    checkpointName = Option("checkpointName")
  )

  protected val checkpointQueryDTO2: CheckpointQueryDTO = CheckpointQueryDTO(
    partitioning = partitioningDTO2,
    limit = Option(5),
    checkpointName = Option("noCheckpoints")
  )

  protected val checkpointQueryDTO3: CheckpointQueryDTO = CheckpointQueryDTO(
    partitioning = partitioningDTO3,
    limit = None,
    checkpointName = None
  )

  // Checkpoint DTO
  protected val checkpointDTO1: CheckpointDTO = CheckpointDTO(
    id = UUID.randomUUID(),
    name = checkpointQueryDTO1.checkpointName.get,
    author = "author",
    measuredByAtumAgent = true,
    partitioning = checkpointQueryDTO1.partitioning,
    processStartTime = ZonedDateTime.now(),
    processEndTime = Some(ZonedDateTime.now()),
    measurements = measurementsDTO1.toSet
  )

  protected val checkpointDTO2: CheckpointDTO = CheckpointDTO(
    id = UUID.randomUUID(),
    name = checkpointQueryDTO2.checkpointName.get,
    author = "author2",
    measuredByAtumAgent = true,
    partitioning = checkpointQueryDTO2.partitioning,
    processStartTime = ZonedDateTime.now(),
    processEndTime = Some(ZonedDateTime.now()),
    measurements = measurementsDTO2.toSet
  )

  protected val checkpointDTO3: CheckpointDTO = checkpointDTO1.copy(id = UUID.randomUUID())
  protected val checkpointDTO4: CheckpointDTO = checkpointDTO1.copy(id = UUID.randomUUID())

  // Checkpoint V2 DTO
  protected val checkpointV2DTO1: CheckpointV2DTO = CheckpointV2DTO(
    id = UUID.randomUUID(),
    name = checkpointQueryDTO1.checkpointName.get,
    author = "author",
    measuredByAtumAgent = true,
    processStartTime = ZonedDateTime.now(),
    processEndTime = Some(ZonedDateTime.now()),
    measurements = measurementsDTO1.toSet
  )

  protected val checkpointV2DTO2: CheckpointV2DTO = CheckpointV2DTO(
    id = UUID.randomUUID(),
    name = checkpointQueryDTO2.checkpointName.get,
    author = "author2",
    measuredByAtumAgent = true,
    processStartTime = ZonedDateTime.now(),
    processEndTime = Some(ZonedDateTime.now()),
    measurements = measurementsDTO2.toSet
  )

  protected val checkpointV2DTO3: CheckpointV2DTO = checkpointV2DTO1.copy(id = UUID.randomUUID())
  protected val checkpointV2DTO4: CheckpointV2DTO = checkpointV2DTO1.copy(id = UUID.randomUUID())

  // Checkpoint From DB
  protected val checkpointFromDB1: CheckpointFromDB = CheckpointFromDB(
    idCheckpoint = Some(checkpointDTO1.id),
    checkpointName = checkpointQueryDTO1.checkpointName,
    author = Some("author"),
    measuredByAtumAgent = Some(true),
    measureName = Some(measureDTO1.measureName),
    measuredColumns = Some(measureDTO1.measuredColumns.toIndexedSeq),
    measurementValue = Some(
      parser
        .parse(
          """
        |{
        |  "mainValue": {
        |    "value": "123",
        |    "valueType": "Long"
        |  },
        |  "supportValues": {
        |    "key1": {
        |      "value": "123456789",
        |      "valueType": "Long"
        |    },
        |    "key2": {
        |      "value": "12345.6789",
        |      "valueType": "BigDecimal"
        |    }
        |  }
        |}
        |""".stripMargin
        )
        .getOrElse {
          throw new Exception("Failed to parse JSON")
        }
    ),
    checkpointStartTime = Some(checkpointDTO1.processStartTime),
    checkpointEndTime = checkpointDTO1.processEndTime
  )
  protected val checkpointFromDB2: CheckpointFromDB = checkpointFromDB1
    .copy(
      idCheckpoint = Some(checkpointDTO2.id),
      checkpointName = checkpointQueryDTO2.checkpointName,
      author = Some("author2"),
      measuredByAtumAgent = Some(true),
      measureName = Some(measureDTO2.measureName),
      measuredColumns = Some(measureDTO2.measuredColumns.toIndexedSeq),
      checkpointStartTime = Some(checkpointDTO2.processStartTime),
      checkpointEndTime = checkpointDTO2.processEndTime
    )

  protected val checkpointFromDB3: CheckpointFromDB = checkpointFromDB1
    .copy(
      idCheckpoint = Some(checkpointDTO3.id),
      checkpointStartTime = Some(checkpointDTO3.processStartTime)
    )

  protected val checkpointItemFromDB1: CheckpointItemFromDB = CheckpointItemFromDB(
    idCheckpoint = checkpointV2DTO1.id,
    checkpointName = checkpointV2DTO1.name,
    author = checkpointV2DTO1.author,
    measuredByAtumAgent = checkpointV2DTO1.measuredByAtumAgent,
    measureName = checkpointV2DTO1.measurements.head.measure.measureName,
    measuredColumns = checkpointV2DTO1.measurements.head.measure.measuredColumns,
    measurementValue = checkpointV2DTO1.measurements.head.result.asJson,
    checkpointStartTime = checkpointV2DTO1.processStartTime,
    checkpointEndTime = checkpointV2DTO1.processEndTime,
    hasMore = true
  )

  protected def createAtumContextDTO(partitioningSubmitDTO: PartitioningSubmitDTO): AtumContextDTO = {
    val measures: Set[MeasureDTO] = Set(MeasureDTO("count", Seq("*")))
    val additionalData: InitialAdditionalDataDTO = Map.empty
    AtumContextDTO(partitioningSubmitDTO.partitioning, measures, additionalData)
  }

  protected def encodePartitioningDTO(partitioningDTO: PartitioningDTO): String = {
    Base64.getUrlEncoder.encodeToString(partitioningDTO.asJson(dto.encodePartitioningDTO).noSpaces.getBytes("UTF-8"))
  }

}
