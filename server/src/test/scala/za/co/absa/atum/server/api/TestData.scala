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

import io.circe.{Json, parser}
import io.circe.syntax.EncoderOps
import za.co.absa.atum.model.dto.MeasureResultDTO.TypedValue
import za.co.absa.atum.model.dto._
import za.co.absa.atum.model.ResultValueType
import za.co.absa.atum.server.api.database.flows.functions.GetFlowPartitionings.GetFlowPartitioningsResult
import za.co.absa.atum.server.model.database._
import za.co.absa.atum.server.api.database.runs.functions.GetPartitioningAncestors.GetPartitioningAncestorsResult

import java.time.ZonedDateTime
import java.util.UUID

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

  protected val partitioningAsJson: Json = parser
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
    id = 1L,
    partitioning = partitioningAsJson,
    author = "author"
  )
  protected val partitioningFromDB2: PartitioningFromDB = PartitioningFromDB(
    id = 1111L,
    partitioning = partitioningAsJson,
    author = "author"
  )

  protected val getFlowPartitioningsResult1: GetFlowPartitioningsResult = GetFlowPartitioningsResult(
    id = 1L,
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

  //Ancestor Partitionings
  protected val getPartitioningAncestorsResult1: GetPartitioningAncestorsResult = GetPartitioningAncestorsResult(
    id = 1L,
    partitioningJson = partitioningAsJson,
    author = "author",
    hasMore = false
  )

  protected val getPartitioningAncestorsResult2: GetPartitioningAncestorsResult = GetPartitioningAncestorsResult(
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
  protected val partitioningWithIdDTO2: PartitioningWithIdDTO = PartitioningWithIdDTO(
    id = partitioningFromDB2.id,
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
  protected val additionalDataDTO1: AdditionalDataDTO = AdditionalDataDTO(
    Seq(
      "key1" -> Some(AdditionalDataItemDTO("value1", Some("author"))),
      "key2" -> None,
      "key3" -> Some(AdditionalDataItemDTO("value3", Some("author")))
    )
  )
  protected val _additionalDataDTO1: Seq[AdditionalDataItemV2DTO] = Seq(
      AdditionalDataItemV2DTO("key1", Some("value1"), Some("author")),
      AdditionalDataItemV2DTO("key2", None, None),
      AdditionalDataItemV2DTO("key3", Some("value3"), Some("author"))
    )


  protected val additionalDataDTO2: AdditionalDataDTO = AdditionalDataDTO(
    Seq(
      "key1" -> Some(AdditionalDataItemDTO("value1", Some("author"))),
      "key2" -> Some(AdditionalDataItemDTO("value2", Some("author"))),
      "key3" -> Some(AdditionalDataItemDTO("value3", Some("author")))
    )
  )

  private val _additionalDataDTO2: Seq[AdditionalDataItemV2DTO] = Seq(
      AdditionalDataItemV2DTO("key1", Some("value1"), Some("author")),
      AdditionalDataItemV2DTO("key2", Some("value2"), Some("author")),
      AdditionalDataItemV2DTO("key3", Some("value3"), Some("author"))
    )

  protected val additionalDataDTO3: AdditionalDataDTO = AdditionalDataDTO(Seq.empty)
  protected val _additionalDataDTO3: Seq[AdditionalDataItemV2DTO] = Seq.empty

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

  // Atum Context
  protected val atumContextDTO1: AtumContextDTO = AtumContextDTO(
    partitioning = partitioningSubmitDTO1.partitioning,
    measures = Set(measureDTO1, measureDTO2),
    additionalData = additionalDataDTOSeq1.toMap
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

  // PartitioningParentPatch DTO
  protected val partitioningParentPatchDTO1: PartitioningParentPatchDTO = PartitioningParentPatchDTO(
    parentPartitioningId = 1L,
    author = "author"
  )

  protected val partitioningParentPatchDTO2: PartitioningParentPatchDTO = PartitioningParentPatchDTO(
    parentPartitioningId = 2L,
    author = "author2"
  )

  protected val partitioningParentPatchDTO3: PartitioningParentPatchDTO = partitioningParentPatchDTO1.copy(author = "differentAuthor")
  protected val partitioningParentPatchDTO4: PartitioningParentPatchDTO = partitioningParentPatchDTO1.copy(author = "yetAnotherAuthor")

  protected val partitioningParentPatchDTO5: PartitioningParentPatchDTO = PartitioningParentPatchDTO(
    parentPartitioningId = 0L,
    author = "NoParent"
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

  protected val measurementValue1: Json =
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
      ).toOption.get

  // Checkpoint From DB
  protected val checkpointItemNotPaginatedFromDB: CheckpointItemFromDB.NotPaginated = CheckpointItemFromDB.NotPaginated(
    idCheckpoint = checkpointV2DTO1.id,
    checkpointName = checkpointV2DTO1.name,
    author = checkpointV2DTO1.author,
    measuredByAtumAgent = checkpointV2DTO1.measuredByAtumAgent,
    measureName = checkpointV2DTO1.measurements.head.measure.measureName,
    measuredColumns = checkpointV2DTO1.measurements.head.measure.measuredColumns,
    measurementValue = checkpointV2DTO1.measurements.head.result.asJson,
    checkpointStartTime = checkpointV2DTO1.processStartTime,
    checkpointEndTime = checkpointV2DTO1.processEndTime
  )

  protected val checkpointItemPaginatedFromDB: CheckpointItemFromDB.Paginated = CheckpointItemFromDB.Paginated(
    idCheckpoint = checkpointV2DTO2.id,
    checkpointName = checkpointV2DTO2.name,
    author = checkpointV2DTO2.author,
    measuredByAtumAgent = checkpointV2DTO2.measuredByAtumAgent,
    measureName = checkpointV2DTO2.measurements.head.measure.measureName,
    measuredColumns = checkpointV2DTO2.measurements.head.measure.measuredColumns,
    measurementValue = checkpointV2DTO2.measurements.head.result.asJson,
    checkpointStartTime = checkpointV2DTO2.processStartTime,
    checkpointEndTime = checkpointV2DTO2.processEndTime,
    hasMore = true
  )

  protected val checkpointItemWithPartitioningFromDB1: CheckpointItemWithPartitioningFromDB =
    CheckpointItemWithPartitioningFromDB(
      idCheckpoint = checkpointItemPaginatedFromDB.idCheckpoint,
      checkpointName = checkpointItemPaginatedFromDB.checkpointName,
      author = checkpointItemPaginatedFromDB.author,
      measuredByAtumAgent = checkpointItemPaginatedFromDB.measuredByAtumAgent,
      measureName = checkpointItemPaginatedFromDB.measureName,
      measuredColumns = checkpointItemPaginatedFromDB.measuredColumns,
      measurementValue = checkpointItemPaginatedFromDB.measurementValue,
      checkpointStartTime = checkpointItemPaginatedFromDB.checkpointStartTime,
      checkpointEndTime = checkpointItemPaginatedFromDB.checkpointEndTime,
      idPartitioning = partitioningFromDB1.id,
      partitioning = partitioningFromDB1.partitioning,
      partitioningAuthor = partitioningFromDB1.author,
      hasMore = checkpointItemPaginatedFromDB.hasMore
    )

  protected val checkpointItemWithPartitioningFromDB2: CheckpointItemWithPartitioningFromDB =
    CheckpointItemWithPartitioningFromDB(
      idCheckpoint = checkpointItemPaginatedFromDB.idCheckpoint,
      checkpointName = checkpointItemPaginatedFromDB.checkpointName,
      author = checkpointItemPaginatedFromDB.author,
      measuredByAtumAgent = checkpointItemPaginatedFromDB.measuredByAtumAgent,
      measureName = checkpointItemPaginatedFromDB.measureName,
      measuredColumns = checkpointItemPaginatedFromDB.measuredColumns,
      measurementValue = checkpointItemPaginatedFromDB.measurementValue,
      checkpointStartTime = checkpointItemPaginatedFromDB.checkpointStartTime,
      checkpointEndTime = checkpointItemPaginatedFromDB.checkpointEndTime,
      idPartitioning = partitioningFromDB2.id,
      partitioning = partitioningFromDB2.partitioning,
      partitioningAuthor = partitioningFromDB2.author,
      hasMore = checkpointItemPaginatedFromDB.hasMore
    )

  protected val checkpointWithPartitioningDTO1: CheckpointWithPartitioningDTO =
    CheckpointWithPartitioningDTO(
      id = checkpointItemWithPartitioningFromDB1.idCheckpoint,
      name = checkpointItemWithPartitioningFromDB1.checkpointName,
      author = checkpointItemWithPartitioningFromDB1.author,
      measuredByAtumAgent = checkpointItemWithPartitioningFromDB1.measuredByAtumAgent,
      processStartTime = checkpointItemWithPartitioningFromDB1.checkpointStartTime,
      processEndTime = checkpointItemWithPartitioningFromDB1.checkpointEndTime,
      measurements = Seq(
        MeasurementDTO(
          measure = MeasureDTO(
            measureName = checkpointItemWithPartitioningFromDB1.measureName,
            measuredColumns = checkpointItemWithPartitioningFromDB1.measuredColumns
          ),
          result = checkpointItemWithPartitioningFromDB1.measurementValue.as[MeasureResultDTO].getOrElse(
            throw new Exception("Failed to parse JSON")
          )
        )
      ).toSet,
      partitioning = partitioningWithIdDTO1
    )

  protected val checkpointWithPartitioningDTO2: CheckpointWithPartitioningDTO =
    CheckpointWithPartitioningDTO(
      id = checkpointItemWithPartitioningFromDB2.idCheckpoint,
      name = checkpointItemWithPartitioningFromDB2.checkpointName,
      author = checkpointItemWithPartitioningFromDB2.author,
      measuredByAtumAgent = checkpointItemWithPartitioningFromDB2.measuredByAtumAgent,
      processStartTime = checkpointItemWithPartitioningFromDB2.checkpointStartTime,
      processEndTime = checkpointItemWithPartitioningFromDB2.checkpointEndTime,
      measurements = Seq(
        MeasurementDTO(
          measure = MeasureDTO(
            measureName = checkpointItemWithPartitioningFromDB2.measureName,
            measuredColumns = checkpointItemWithPartitioningFromDB2.measuredColumns
          ),
          result = checkpointItemWithPartitioningFromDB2.measurementValue.as[MeasureResultDTO].getOrElse(
            throw new Exception("Failed to parse JSON")
          )
        )
      ).toSet,
      partitioning = partitioningWithIdDTO1
    )

  protected def createAtumContextDTO(partitioningSubmitDTO: PartitioningSubmitDTO): AtumContextDTO = {
    val measures: Set[MeasureDTO] = Set(MeasureDTO("count", Seq("*")))
    val additionalData: Map[String, Option[String]] = Map.empty
    AtumContextDTO(partitioningSubmitDTO.partitioning, measures, additionalData)
  }

}
