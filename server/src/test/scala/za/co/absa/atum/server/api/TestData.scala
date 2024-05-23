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

import za.co.absa.atum.model.dto._
import za.co.absa.atum.server.model.CheckpointFromDB

import java.time.ZonedDateTime
import java.util.UUID
import io.circe.parser._

trait TestData {

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
  protected val partitioningSubmitDTO2: PartitioningSubmitDTO =
    partitioningSubmitDTO1.copy(authorIfNew = "differentAuthor")

  protected val partitioningSubmitDTO3: PartitioningSubmitDTO =
    partitioningSubmitDTO1.copy(authorIfNew = "yetAnotherAuthor")

  // Measure
  protected val measureDTO1: MeasureDTO = MeasureDTO("count", Seq("1"))
  protected val measureDTO2: MeasureDTO = MeasureDTO("count", Seq("*"))

  // Additional Data
  protected val additionalDataDTO1: AdditionalDataDTO = Map(
    "key1" -> Some("value1"),
    "key2" -> None,
    "key3" -> Some("value3")
  )
  protected  val additionalDataDTO2: AdditionalDataDTO = Map(
    "key1" -> Some("value1"),
    "key2" -> Some("value2"),
    "key3" -> Some("value3")
  )
  protected val additionalDataDTO3: AdditionalDataDTO = Map.empty

  // Additional Data DTO as a sequence
  protected val additionalDataDTOSeq1: Seq[(String, Option[String])] = Seq(
    "key1" -> Some("value1"),
    "key2" -> None,
    "key3" -> Some("value3")
  )

  // Additional Data submit DTO
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
    partitioning = partitioningSubmitDTO1.partitioning,
    measures = Set(MeasureDTO("count", Seq("1")))
  )

  // Checkpoint
  protected val checkpointDTO1: CheckpointDTO = CheckpointDTO(
    id = UUID.randomUUID(),
    name = "name",
    author = "author",
    partitioning = Seq.empty,
    measuredByAtumAgent = true,
    processStartTime = ZonedDateTime.now(),
    processEndTime = None,
    measurements = Set(
      MeasurementDTO(
        MeasureDTO(measureName = "cnt", measuredColumns = Seq("col1", "col2")),
        MeasureResultDTO(
          mainValue = MeasureResultDTO.TypedValue("123", MeasureResultDTO.ResultValueType.Long),
          supportValues = Map(
            "help1" -> MeasureResultDTO.TypedValue("666", MeasureResultDTO.ResultValueType.Long),
            "help2" -> MeasureResultDTO.TypedValue("99.9", MeasureResultDTO.ResultValueType.Double),
          )
        )
      )
    )
  )
  protected val checkpointDTO2: CheckpointDTO = checkpointDTO1.copy(id = UUID.randomUUID(), partitioning = partitioningDTO1)

  protected val checkpointDTO3: CheckpointDTO = checkpointDTO1.copy(id = UUID.randomUUID())

  protected val checkpointQueryDTO1: CheckpointQueryDTO = CheckpointQueryDTO(
    partitioning = Seq.empty,
    limit = Some(5),
    checkpointName = None
  )

  protected val checkpointQueryDTO2: CheckpointQueryDTO = checkpointQueryDTO1.copy(partitioning = partitioningDTO1)
  protected val checkpointQueryDTO3: CheckpointQueryDTO = checkpointQueryDTO1.copy(partitioning = partitioningDTO2)

  protected val checkpointFromDB1: CheckpointFromDB = CheckpointFromDB(
    idCheckpoint = checkpointDTO1.id,
    checkpointName = "name",
    author = "author",
    measuredByAtumAgent = true,
    measureName = "cnt",
    measuredColumns = Seq("col1", "col2"),
    measurementValue = parse(
      """
        |{
        |  "mainValue": {
        |    "value": "123",
        |    "valueType": "Long"
        |  },
        |  "supportValues": {
        |    "help1": {
        |      "value": "666",
        |      "valueType": "Long"
        |    },
        |    "help2": {
        |      "value": "99.9",
        |      "valueType": "Double"
        |    }
        |  }
        |}
        |""".stripMargin
    ).getOrElse {
      throw new Exception("Failed to parse JSON")
    },
    checkpointStartTime = checkpointDTO1.processStartTime,
    checkpointEndTime = None,
  )
  protected val checkpointFromDB2: CheckpointFromDB = checkpointFromDB1
    .copy(idCheckpoint = checkpointDTO2.id, checkpointStartTime = checkpointDTO2.processStartTime)

  protected val checkpointFromDB3: CheckpointFromDB = checkpointFromDB1
    .copy(idCheckpoint = checkpointDTO3.id, checkpointStartTime = checkpointDTO3.processStartTime)


  protected def createAtumContextDTO(partitioningSubmitDTO: PartitioningSubmitDTO): AtumContextDTO = {
    val measures: Set[MeasureDTO] = Set(MeasureDTO("count", Seq("*")))
    val additionalData: AdditionalDataDTO = Map.empty
    AtumContextDTO(partitioningSubmitDTO.partitioning, measures, additionalData)
  }

}
