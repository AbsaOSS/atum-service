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

package za.co.absa.atum.model.utils

import org.scalatest.flatspec.AnyFlatSpecLike
import za.co.absa.atum.model.dto.MeasureResultDTO.{ResultValueType, TypedValue}
import za.co.absa.atum.model.dto._
import SerializationUtilsTest.StringLinearization

import java.time.{ZoneId, ZoneOffset, ZonedDateTime}
import java.util.UUID

class SerializationUtilsTest extends AnyFlatSpecLike {

  // AdditionalDataDTO
  "asJson" should "serialize AdditionalDataDTO into json string" in {
    val additionalDataDTO = AdditionalDataDTO(
      Map[String, Option[String]](
        "key1" -> Some("val1"),
        "key2" -> Some("val2"),
        "key3" -> None
      )
    )

    val expectedAdditionalDataJson = """{"additionalData":{"key1":"val1","key2":"val2"}}"""
    val actualAdditionalDataJson = SerializationUtils.asJson(additionalDataDTO)

    assert(expectedAdditionalDataJson == actualAdditionalDataJson)
  }

  "fromJson" should "deserialize AdditionalDataDTO from json string" in {
    val additionalDataDTOJson = """{"additionalData":{"key1":"val1","key2":"val2"}}"""
    val expectedAdditionalDataDTO = AdditionalDataDTO(
      Map[String, Option[String]](
        "key1" -> Some("val1"),
        "key2" -> Some("val2")
      )
    )
    val actualAdditionalDataDTO = SerializationUtils.fromJson[AdditionalDataDTO](additionalDataDTOJson)

    assert(expectedAdditionalDataDTO == actualAdditionalDataDTO)
  }

  "asJson" should "serialize empty AdditionalDataDTO into json string" in {
    val additionalDataDTO = AdditionalDataDTO(Map.empty)

    val expectedAdditionalDataJson = """{"additionalData":{}}"""
    val actualAdditionalDataJson = SerializationUtils.asJson(additionalDataDTO)

    assert(expectedAdditionalDataJson == actualAdditionalDataJson)
  }

  "fromJson" should "deserialize empty AdditionalDataDTO from json string" in {
    val additionalDataDTOJsonString = """{"additionalData":{}}"""
    val expectedAdditionalDataDTO = AdditionalDataDTO(Map.empty)

    val actualAdditionalDataDTO = SerializationUtils.fromJson[AdditionalDataDTO](additionalDataDTOJsonString)

    assert(expectedAdditionalDataDTO == actualAdditionalDataDTO)
  }

  // AtumContextDTO
  "asJson" should "serialize AtumContextDTO into json string" in {
    val seqPartitionDTO = Seq(PartitionDTO("key", "val"))
    val seqMeasureDTO = Set(MeasureDTO("count", Seq("col")))

    val atumContextDTO = AtumContextDTO(partitioning = seqPartitionDTO, measures = seqMeasureDTO)

    val expectedAdditionalDataJson =
      """
        |{
        |"partitioning":[{"key":"key","value":"val"}],
        |"measures":[{"measureName":"count","measuredColumns":["col"]}],
        |"additionalData":{"additionalData":{}}
        |}""".linearize
    val actualAdditionalDataJson = SerializationUtils.asJson(atumContextDTO)

    assert(expectedAdditionalDataJson == actualAdditionalDataJson)
  }

  "fromJson" should "deserialize AtumContextDTO from json string" in {
    val atumContextDTOJson =
      """
        |{"partitioning":[{"key":"key","value":"val"}],
        |"measures":[{"measureName":"count","measuredColumns":["col"]}],
        |"additionalData":{"additionalData":{}}}
        |""".stripMargin

    val seqPartitionDTO = Seq(PartitionDTO("key", "val"))
    val seqMeasureDTO = Set(MeasureDTO("count", Seq("col")))

    val expectedAtumContextDTO = AtumContextDTO(partitioning = seqPartitionDTO, measures = seqMeasureDTO)

    val actualAtumContextDTO = SerializationUtils.fromJson[AtumContextDTO](atumContextDTOJson)

    assert(expectedAtumContextDTO == actualAtumContextDTO)
  }

  "asJson" should "serialize AtumContextDTO without measures into json string" in {
    val seqPartitionDTO = Seq(PartitionDTO("key", "val"))

    val atumContextDTO = AtumContextDTO(partitioning = seqPartitionDTO)

    val expectedAdditionalDataJson = """{"partitioning":[{"key":"key","value":"val"}],"measures":[],"additionalData":{"additionalData":{}}}"""
    val actualAdditionalDataJson = SerializationUtils.asJson(atumContextDTO)

    assert(expectedAdditionalDataJson == actualAdditionalDataJson)
  }

  "fromJson" should "deserialize AtumContextDTO without measures from json string" in {
    val atumContextDTOJson = """{"partitioning":[{"key":"key","value":"val"}],"measures":[],"additionalData":{"additionalData":{}}}"""

    val expectedSeqPartitionDTO = Seq(PartitionDTO("key", "val"))

    val expectedAtumContextDTO = AtumContextDTO(partitioning = expectedSeqPartitionDTO)
    val actualAtumContextDTO = SerializationUtils.fromJson[AtumContextDTO](atumContextDTOJson)

    assert(expectedAtumContextDTO == actualAtumContextDTO)
  }

  // CheckpointDTO
  "asJson" should "serialize CheckpointDTO into json string" in {
    val uuid = UUID.randomUUID()
    val seqPartitionDTO = Seq(PartitionDTO("key", "val"))
    val timeWithZone = ZonedDateTime.of(2023, 10, 24, 10, 20, 59, 5000000, ZoneId.of("CET"))

    val seqMeasurementDTO = Seq(
      MeasurementDTO(
        measure = MeasureDTO("count", Seq("col")), result = MeasureResultDTO(
          mainValue = TypedValue("1", ResultValueType.Long)
        )
      )
    )

    val checkpointDTO = CheckpointDTO(
      id = uuid,
      name = "checkpoint",
      author = "author",
      measuredByAtumAgent = true,
      partitioning = seqPartitionDTO,
      processStartTime = timeWithZone,
      processEndTime = Some(timeWithZone),
      measurements = seqMeasurementDTO
    )

    val expectedCheckpointDTOJson =
      s"""
         |{
         |"id":"$uuid",
         |"name":"checkpoint",
         |"author":"author",
         |"measuredByAtumAgent":true,
         |"partitioning":[{"key":"key","value":"val"}],
         |"processStartTime":"2023-10-24T10:20:59.005+02:00[CET]",
         |"processEndTime":"2023-10-24T10:20:59.005+02:00[CET]",
         |"measurements":[{"measure":{"measureName":"count","measuredColumns":["col"]},
         |"result":{"mainValue":{"value":"1","valueType":"Long"},"supportValues":{}}}]
         |}
         |""".linearize
    val actualCheckpointDTOJson = SerializationUtils.asJson(checkpointDTO)

    assert(actualCheckpointDTOJson == expectedCheckpointDTOJson)
  }

  "fromJson" should "deserialize CheckpointDTO from json string" in {
    val uuid = UUID.randomUUID()
    val seqPartitionDTO = Seq(PartitionDTO("key", "val"))
    val timeWithZone = ZonedDateTime.of(2023, 10, 24, 10, 20, 59, 5000000, ZoneOffset.ofHours(2))

    val checkpointDTOJson =
      s"""
         |{
         |"id":"$uuid",
         |"name":"checkpoint",
         |"author":"author",
         |"measuredByAtumAgent":true,
         |"partitioning":[{"key":"key","value":"val"}],
         |"processStartTime":"2023-10-24T10:20:59.005+02:00",
         |"processEndTime":"2023-10-24T10:20:59.005+02:00",
         |"measurements":[{"measure":{"measureName":"count","measuredColumns":["col"]},
         |"result":{"mainValue":{"value":"1","valueType":"Long"},"supportValues":{}}}]
         |}
         |""".linearize


    val seqMeasurementDTO = Seq(
      MeasurementDTO(
        measure = MeasureDTO("count", Seq("col")), result = MeasureResultDTO(
          mainValue = TypedValue("1", ResultValueType.Long)
        )
      )
    )

    val expectedCheckpointDTO = CheckpointDTO(
      id = uuid,
      name = "checkpoint",
      author = "author",
      measuredByAtumAgent = true,
      partitioning = seqPartitionDTO,
      processStartTime = timeWithZone,
      processEndTime = Some(timeWithZone),
      measurements = seqMeasurementDTO
    )

    val actualCheckpointDTO = SerializationUtils.fromJson[CheckpointDTO](checkpointDTOJson)

    assert(actualCheckpointDTO == expectedCheckpointDTO)
  }

  // MeasureDTO
  "asJson" should "serialize MeasureDTO into json string" in {
    val measureDTO = MeasureDTO("count", Seq("col"))

    val expectedMeasureDTOJson = """{"measureName":"count","measuredColumns":["col"]}"""
    val actualMeasureDTOJson = SerializationUtils.asJson(measureDTO)

    assert(expectedMeasureDTOJson == actualMeasureDTOJson)
  }

  "fromJson" should "deserialize MeasureDTO from json string" in {
    val measureDTOJson = """{"measureName":"count","measuredColumns":["col"]}"""

    val expectedMeasureDTO = MeasureDTO("count", Seq("col"))
    val actualMeasureDTO = SerializationUtils.fromJson[MeasureDTO](measureDTOJson)

    assert(expectedMeasureDTO == actualMeasureDTO)
  }

  // MeasurementDTO
  "asJson" should "serialize MeasurementDTO into json string" in {
    val measureDTO = MeasureDTO("count", Seq("col"))
    val measureResultDTO = MeasureResultDTO(mainValue = TypedValue("1", ResultValueType.Long))

    val measurementDTO = MeasurementDTO(measureDTO, measureResultDTO)

    val expectedMeasurementDTOJson =
      """
        |{
        |"measure":{"measureName":"count","measuredColumns":["col"]},
        |"result":{"mainValue":{"value":"1","valueType":"Long"},
        |"supportValues":{}}
        |}
        |""".linearize
    val actualMeasurementDTOJson = SerializationUtils.asJson(measurementDTO)

    assert(expectedMeasurementDTOJson == actualMeasurementDTOJson)
  }

  "fromJson" should "deserialize MeasurementDTO from json string" in {
    val measurementDTOJson =
      """
        |{
        |"measure":{"measureName":"count","measuredColumns":["col"]},
        |"result":{"mainValue":{"value":"1","valueType":"Long"},"supportValues":{}}
        |}
        |""".stripMargin

    val measureDTO = MeasureDTO("count", Seq("col"))
    val measureResultDTO = MeasureResultDTO(mainValue = TypedValue("1", ResultValueType.Long))

    val expectedMeasurementDTO = MeasurementDTO(measureDTO, measureResultDTO)
    val actualMeasurementDTO = SerializationUtils.fromJson[MeasurementDTO](measurementDTOJson)

    assert(expectedMeasurementDTO == actualMeasurementDTO)
  }

  // MeasureResultDTO
  "asJson" should "serialize MeasureResultDTO into json string" in {
    val measureResultDTO = MeasureResultDTO(mainValue = TypedValue("1", ResultValueType.Long))

    val expectedMeasureResultDTOJson = """{"mainValue":{"value":"1","valueType":"Long"},"supportValues":{}}"""
    val actualMeasureResultDTOJson = SerializationUtils.asJson(measureResultDTO)

    assert(expectedMeasureResultDTOJson == actualMeasureResultDTOJson)
  }

  "fromJson" should "deserialize MeasureResultDTO from json string" in {
    val measureResultDTOJson = """{"mainValue":{"value":"1","valueType":"Long"},"supportValues":{}}"""

    val expectedMeasureResultDTO = MeasureResultDTO(mainValue = TypedValue("1", ResultValueType.Long))
    val actualMeasureResultDTO = SerializationUtils.fromJson[MeasureResultDTO](measureResultDTOJson)

    assert(expectedMeasureResultDTO == actualMeasureResultDTO)
  }

  // PartitionDTO
  "asJson" should "serialize PartitionDTO into json string" in {
    val partitionDTO = PartitionDTO("key", "val")

    val expectedPartitionDTOJson = """{"key":"key","value":"val"}"""
    val actualPartitionDTOJson = SerializationUtils.asJson(partitionDTO)

    assert(expectedPartitionDTOJson == actualPartitionDTOJson)
  }

  "fromJson" should "deserialize PartitionDTO from json string" in {
    val partitionDTOJson = """{"key":"key","value":"val"}"""

    val expectedPartitionDTO = PartitionDTO("key", "val")
    val actualPartitionDTO = SerializationUtils.fromJson[PartitionDTO](partitionDTOJson)

    assert(expectedPartitionDTO == actualPartitionDTO)
  }

  // PartitioningDTO
  "asJson" should "serialize PartitioningDTO into json string" in {
    val partitionDTO = PartitionDTO("key", "val")

    val partitioningDTO = PartitioningSubmitDTO(
      partitioning = Seq(partitionDTO),
      parentPartitioning = None,
      authorIfNew = "authorTest"
    )

    val expectedPartitioningDTOJson = """{"partitioning":[{"key":"key","value":"val"}],"authorIfNew":"authorTest"}"""
    val actualPartitioningDTOJson = SerializationUtils.asJson(partitioningDTO)

    assert(expectedPartitioningDTOJson == actualPartitioningDTOJson)
  }

  "fromJson" should "deserialize PartitioningDTO from json string" in {
    val partitioningDTOJson = """{"partitioning":[{"key":"key","value":"val"}],"authorIfNew":"authorTest"}"""

    val expectedPartitionDTO = PartitionDTO("key", "val")
    val expectedPartitioningDTO = PartitioningSubmitDTO(
      partitioning = Seq(expectedPartitionDTO),
      parentPartitioning = None,
      authorIfNew = "authorTest"
    )

    val actualPartitioningDTO = SerializationUtils.fromJson[PartitioningSubmitDTO](partitioningDTOJson)

    assert(expectedPartitioningDTO == actualPartitioningDTO)
  }

  "asJson" should "serialize PartitioningDTO with parent partitioning into json string" in {
    val partitionDTO = PartitionDTO("key", "val")
    val parentPartitionDTO = PartitionDTO("parentKey", "parentVal")

    val partitioningDTO = PartitioningSubmitDTO(
      partitioning = Seq(partitionDTO),
      parentPartitioning = Some(Seq(parentPartitionDTO)),
      authorIfNew = "authorTest"
    )

    val expectedPartitioningDTOJson =
      """
        |{
        |"partitioning":[{"key":"key","value":"val"}],
        |"parentPartitioning":[{"key":"parentKey","value":"parentVal"}],
        |"authorIfNew":"authorTest"
        |}
        |""".linearize
    val actualPartitioningDTOJson = SerializationUtils.asJson(partitioningDTO)

    assert(expectedPartitioningDTOJson == actualPartitioningDTOJson)
  }


  "asJson" should "serialize Seq[PartitionDTO] into json string" in {
    val partitionDTO = Seq(
      PartitionDTO("key1", "val1"),
      PartitionDTO("key2", "val2"),
      PartitionDTO("key3", "val3")
    )

    val expectedPartitionDTOJson = """[{"key":"key1","value":"val1"},{"key":"key2","value":"val2"},{"key":"key3","value":"val3"}]"""
    val actualPartitionDTOJson = SerializationUtils.asJson(partitionDTO)

    assert(expectedPartitionDTOJson == actualPartitionDTOJson)
  }

  "fromJson" should "deserialize PartitioningDTO with parent partitioning from json string" in {
    val partitioningDTOJson =
      """
        |{
        |"partitioning":[{"key":"key","value":"val"}],
        |"parentPartitioning":[{"key":"parentKey","value":"parentVal"}],
        |"authorIfNew":"authorTest"
        |}
        |""".stripMargin.replace("\r\n", "")

    val expectedPartitionDTO = PartitionDTO("key", "val")
    val expectedParentPartitionDTO = PartitionDTO("parentKey", "parentVal")
    val expectedPartitioningDTO = PartitioningSubmitDTO(
      partitioning = Seq(expectedPartitionDTO),
      parentPartitioning = Some(Seq(expectedParentPartitionDTO)),
      authorIfNew = "authorTest"
    )

    val actualPartitioningDTO = SerializationUtils.fromJson[PartitioningSubmitDTO](partitioningDTOJson)

    assert(expectedPartitioningDTO == actualPartitioningDTO)
  }

}

object SerializationUtilsTest {
  implicit class StringLinearization(val str: String) extends AnyVal {
    def linearize: String = {
      str.stripMargin.replace("\r", "").replace("\n", "")
    }
  }
}
