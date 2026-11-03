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

package za.co.absa.atum.model.dto

import org.scalatest.flatspec.AnyFlatSpecLike
import za.co.absa.atum.model.ResultValueType
import za.co.absa.atum.model.dto.MeasureResultDTO.TypedValue
import za.co.absa.atum.model.utils.JsonSyntaxExtensions._
import za.co.absa.atum.testing.implicits.StringImplicits.StringLinearization

import java.time.{ZoneId, ZoneOffset, ZonedDateTime}
import java.util.UUID

import io.circe.parser._
import io.circe.syntax._

class SerializationUtilsUnitTests extends AnyFlatSpecLike {

  // AdditionalDataDTO
  "asJsonString" should "serialize AdditionalDataDTO into json string" in {
    val data: AdditionalDataDTO.Data = Map(
      "key1" -> Some(AdditionalDataItemDTO("val1", "testAuthor")),
      "key2" -> Some(AdditionalDataItemDTO("val2", "testAuthor")),
      "key3" -> None
    )

    val additionalDataDTO = AdditionalDataDTO(data)

    val expectedAdditionalDataJson =
      """
        |{"data":
        |{"key1":{"value":"val1","author":"testAuthor"},"key2":{"value":"val2","author":"testAuthor"},"key3":null}}
        |""".linearize
    val actualAdditionalDataJson = additionalDataDTO.asJsonString

    assert(actualAdditionalDataJson == expectedAdditionalDataJson)
  }

  "fromJson" should "deserialize AdditionalDataDTO from json string" in {
    val additionalDataDTOJson =
      """
        |{"data":{"key1":{"value":"val1","author":"testAuthor"},"key2":{"value":"val2","author":"testAuthor"}}}
        |""".stripMargin

    val data: AdditionalDataDTO.Data = Map(
      "key1" -> Some(AdditionalDataItemDTO("val1", "testAuthor")),
      "key2" -> Some(AdditionalDataItemDTO("val2", "testAuthor"))
    )

    val expectedAdditionalDataDTO = AdditionalDataDTO(data)
    val actualAdditionalDataDTO = additionalDataDTOJson.as[AdditionalDataDTO]

    assert(actualAdditionalDataDTO == expectedAdditionalDataDTO)
  }

  "asJsonString" should "serialize empty AdditionalDataDTO into json string" in {
    val expectedAdditionalDataJson = """{"data":{}}"""

    val additionalDataDTO = AdditionalDataDTO(Map.empty)
    val actualAdditionalDataJson = additionalDataDTO.asJsonString

    assert(actualAdditionalDataJson == expectedAdditionalDataJson)
  }

  "fromJson" should "deserialize empty AdditionalDataDTO from json string" in {
    val expectedAdditionalDataDTO = AdditionalDataDTO(Map.empty)

    val additionalDataDTOJsonString = """{"data":{}}"""
    val actualAdditionalDataDTO = additionalDataDTOJsonString.as[AdditionalDataDTO]

    assert(actualAdditionalDataDTO == expectedAdditionalDataDTO)
  }

  // AtumContextDTO
  "asJsonString" should "serialize AtumContextDTO into json string" in {
    val seqPartitionDTO = Seq(PartitionDTO("key", "val"))
    val seqMeasureDTO = Set(MeasureDTO("count", Seq("col")))

    val atumContextDTO = AtumContextDTO(partitioning = seqPartitionDTO, measures = seqMeasureDTO)

    val expectedAdditionalDataJson =
      """
        |{
        |"partitioning":[{"key":"key","value":"val"}],
        |"measures":[{"measureName":"count","measuredColumns":["col"]}],
        |"additionalData":{}
        |}""".linearize
    val actualAdditionalDataJson = atumContextDTO.asJsonString

    assert(actualAdditionalDataJson == expectedAdditionalDataJson)
  }

  "fromJson" should "deserialize AtumContextDTO from json string" in {
    val atumContextDTOJson =
      """
        |{
        |"partitioning":[{"key":"key","value":"val"}],
        |"measures":[{"measureName":"count","measuredColumns":["col"]}],
        |"additionalData":{}
        |}""".stripMargin

    val seqPartitionDTO = Seq(PartitionDTO("key", "val"))
    val seqMeasureDTO = Set(MeasureDTO("count", Seq("col")))

    val expectedAtumContextDTO = AtumContextDTO(partitioning = seqPartitionDTO, measures = seqMeasureDTO)

    val actualAtumContextDTO = atumContextDTOJson.as[AtumContextDTO]

    assert(actualAtumContextDTO == expectedAtumContextDTO)
  }

  "asJsonString" should "serialize AtumContextDTO without measures into json string" in {
    val seqPartitionDTO = Seq(PartitionDTO("key", "val"))

    val atumContextDTO = AtumContextDTO(partitioning = seqPartitionDTO)

    val expectedAdditionalDataJson =
      """{"partitioning":[{"key":"key","value":"val"}],"measures":[],"additionalData":{}}"""
    val actualAdditionalDataJson = atumContextDTO.asJsonString

    assert(actualAdditionalDataJson == expectedAdditionalDataJson)
  }

  "fromJson" should "deserialize AtumContextDTO without measures from json string" in {
    val atumContextDTOJson = """{"partitioning":[{"key":"key","value":"val"}],"measures":[],"additionalData":{}}"""

    val expectedSeqPartitionDTO = Seq(PartitionDTO("key", "val"))

    val expectedAtumContextDTO = AtumContextDTO(partitioning = expectedSeqPartitionDTO)
    val actualAtumContextDTO = atumContextDTOJson.as[AtumContextDTO]

    assert(actualAtumContextDTO == expectedAtumContextDTO)
  }

  // CheckpointDTO
  "asJsonString" should "serialize CheckpointDTO into json string" in {
    val uuid = UUID.randomUUID()
    val seqPartitionDTO = Seq(PartitionDTO("key", "val"))
    val timeWithZone = ZonedDateTime.of(2023, 10, 24, 10, 20, 59, 5000000, ZoneId.of("CET"))

    val setMeasurementDTO = Set(
      MeasurementDTO(
        measure = MeasureDTO("count", Seq("col")),
        result = MeasureResultDTO(
          mainValue = TypedValue("1", ResultValueType.LongValue)
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
      measurements = setMeasurementDTO
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
         |"result":{"mainValue":{"value":"1","valueType":"Long"},"supportValues":{}}}],
         |"properties": null
         |}
         |""".linearize
    val actualCheckpointDTOJson = checkpointDTO.asJsonString

    assert(parse(actualCheckpointDTOJson) == parse(expectedCheckpointDTOJson))
  }

  "asJsonString" should "serialize CheckpointDTO with properties into json string" in {
    val uuid = UUID.randomUUID()
    val seqPartitionDTO = Seq(PartitionDTO("key", "val"))
    val timeWithZone = ZonedDateTime.of(2023, 10, 24, 10, 20, 59, 5000000, ZoneId.of("CET"))

    val setMeasurementDTO = Set(
      MeasurementDTO(
        measure = MeasureDTO("count", Seq("col")),
        result = MeasureResultDTO(
          mainValue = TypedValue("1", ResultValueType.LongValue)
        )
      )
    )

    val properties = Some(Map("propKey1" -> "propVal1", "propKey2" -> "propVal2"))

    val checkpointDTO = CheckpointDTO(
      id = uuid,
      name = "checkpoint",
      author = "author",
      measuredByAtumAgent = true,
      partitioning = seqPartitionDTO,
      processStartTime = timeWithZone,
      processEndTime = Some(timeWithZone),
      measurements = setMeasurementDTO,
      properties = properties
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
         |"result":{"mainValue":{"value":"1","valueType":"Long"},"supportValues":{}}}],
         |"properties": {
         |    "propKey1" : "propVal1",
         |    "propKey2" : "propVal2"
         |  }
         |}
         |""".linearize
    val actualCheckpointDTOJson = checkpointDTO.asJsonString

    assert(parse(actualCheckpointDTOJson) == parse(expectedCheckpointDTOJson))
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
         |""".stripMargin

    val setMeasurementDTO = Set(
      MeasurementDTO(
        measure = MeasureDTO("count", Seq("col")),
        result = MeasureResultDTO(
          mainValue = TypedValue("1", ResultValueType.LongValue)
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
      measurements = setMeasurementDTO
    )

    val actualCheckpointDTO = checkpointDTOJson.as[CheckpointDTO]

    assert(actualCheckpointDTO == expectedCheckpointDTO)
  }

  "fromJson" should "deserialize CheckpointDTO with properties from json string" in {
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
         |"result":{"mainValue":{"value":"1","valueType":"Long"},"supportValues":{}}}],
         |"properties": {
         |    "propKey1" : "propVal1",
         |    "propKey2" : "propVal2"
         |  }
         |}
         |""".stripMargin

    val setMeasurementDTO = Set(
      MeasurementDTO(
        measure = MeasureDTO("count", Seq("col")),
        result = MeasureResultDTO(
          mainValue = TypedValue("1", ResultValueType.LongValue)
        )
      )
    )

    val properties = Some(Map("propKey1" -> "propVal1", "propKey2" -> "propVal2"))

    val expectedCheckpointDTO = CheckpointDTO(
      id = uuid,
      name = "checkpoint",
      author = "author",
      measuredByAtumAgent = true,
      partitioning = seqPartitionDTO,
      processStartTime = timeWithZone,
      processEndTime = Some(timeWithZone),
      measurements = setMeasurementDTO,
      properties = properties
    )

    val actualCheckpointDTO = checkpointDTOJson.as[CheckpointDTO]

    assert(actualCheckpointDTO == expectedCheckpointDTO)
  }

  // MeasureDTO
  "asJsonString" should "serialize MeasureDTO into json string" in {
    val measureDTO = MeasureDTO("count", Seq("col"))

    val expectedMeasureDTOJson = """{"measureName":"count","measuredColumns":["col"]}"""
    val actualMeasureDTOJson = measureDTO.asJsonString

    assert(actualMeasureDTOJson == expectedMeasureDTOJson)
  }

  "fromJson" should "deserialize MeasureDTO from json string" in {
    val measureDTOJson = """{"measureName":"count","measuredColumns":["col"]}"""

    val expectedMeasureDTO = MeasureDTO("count", Seq("col"))
    val actualMeasureDTO = measureDTOJson.as[MeasureDTO]

    assert(actualMeasureDTO == expectedMeasureDTO)
  }

  // MeasurementDTO
  "asJsonString" should "serialize MeasurementDTO into json string" in {
    val measureDTO = MeasureDTO("count", Seq("col"))
    val measureResultDTO = MeasureResultDTO(mainValue = TypedValue("1", ResultValueType.LongValue))

    val measurementDTO = MeasurementDTO(measureDTO, measureResultDTO)

    val expectedMeasurementDTOJson =
      """
        |{
        |"measure":{"measureName":"count","measuredColumns":["col"]},
        |"result":{"mainValue":{"value":"1","valueType":"Long"},
        |"supportValues":{}}
        |}
        |""".linearize
    val actualMeasurementDTOJson = measurementDTO.asJsonString

    assert(actualMeasurementDTOJson == expectedMeasurementDTOJson)
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
    val measureResultDTO = MeasureResultDTO(mainValue = TypedValue("1", ResultValueType.LongValue))

    val expectedMeasurementDTO = MeasurementDTO(measureDTO, measureResultDTO)
    val actualMeasurementDTO = measurementDTOJson.as[MeasurementDTO]

    assert(actualMeasurementDTO == expectedMeasurementDTO)
  }

  // MeasureResultDTO
  "asJsonString" should "serialize MeasureResultDTO into json string" in {
    val measureResultDTO = MeasureResultDTO(mainValue = TypedValue("1", ResultValueType.LongValue))

    val expectedMeasureResultDTOJson = """{"mainValue":{"value":"1","valueType":"Long"},"supportValues":{}}"""
    val actualMeasureResultDTOJson = measureResultDTO.asJsonString

    assert(actualMeasureResultDTOJson == expectedMeasureResultDTOJson)
  }

  "fromJson" should "deserialize MeasureResultDTO from json string" in {
    val measureResultDTOJson = """{"mainValue":{"value":"1","valueType":"Long"},"supportValues":{}}"""

    val expectedMeasureResultDTO = MeasureResultDTO(mainValue = TypedValue("1", ResultValueType.LongValue))
    val actualMeasureResultDTO = measureResultDTOJson.as[MeasureResultDTO]

    assert(actualMeasureResultDTO == expectedMeasureResultDTO)
  }

  // PartitionDTO
  "asJsonString" should "serialize PartitionDTO into json string" in {
    val partitionDTO = PartitionDTO("key", "val")

    val expectedPartitionDTOJson = """{"key":"key","value":"val"}"""
    val actualPartitionDTOJson = partitionDTO.asJsonString

    assert(actualPartitionDTOJson == expectedPartitionDTOJson)
  }

  "fromJson" should "deserialize PartitionDTO from json string" in {
    val partitionDTOJson = """{"key":"key","value":"val"}"""

    val expectedPartitionDTO = PartitionDTO("key", "val")
    val actualPartitionDTO = partitionDTOJson.as[PartitionDTO]

    assert(actualPartitionDTO == expectedPartitionDTO)
  }

  // PartitioningDTO
  "asJsonString" should "serialize PartitioningDTO into json string" in {
    val partitionDTO = PartitionDTO("key", "val")

    val partitioningDTO = PartitioningSubmitDTO(
      partitioning = Seq(partitionDTO),
      parentPartitioning = None,
      authorIfNew = "authorTest"
    )

    val expectedPartitioningDTOJson =
      """{"partitioning":[{"key":"key","value":"val"}],"parentPartitioning":null,"authorIfNew":"authorTest"}"""
    val actualPartitioningDTOJson = partitioningDTO.asJsonString

    assert(actualPartitioningDTOJson == expectedPartitioningDTOJson)
  }

  "fromJson" should "deserialize PartitioningDTO from json string" in {
    val partitioningDTOJson = """{"partitioning":[{"key":"key","value":"val"}],"authorIfNew":"authorTest"}"""

    val expectedPartitionDTO = PartitionDTO("key", "val")
    val expectedPartitioningDTO = PartitioningSubmitDTO(
      partitioning = Seq(expectedPartitionDTO),
      parentPartitioning = None,
      authorIfNew = "authorTest"
    )

    val actualPartitioningDTO = partitioningDTOJson.as[PartitioningSubmitDTO]

    assert(actualPartitioningDTO == expectedPartitioningDTO)
  }

  "asJsonString" should "serialize PartitioningDTO with parent partitioning into json string" in {
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
    val actualPartitioningDTOJson = partitioningDTO.asJsonString

    assert(actualPartitioningDTOJson == expectedPartitioningDTOJson)
  }

  "fromJson" should "decode a literal v0.10.0 v1 body with and without parentPartitioning" in {
    val nullJson = """{"partitioning":[{"key":"k","value":"v"}],"parentPartitioning":null,"authorIfNew":"a"}"""
    val parentJson =
      """{"partitioning":[{"key":"k","value":"v"}],"parentPartitioning":[{"key":"p","value":"v"}],"authorIfNew":"a"}"""
    val missingJson = """{"partitioning":[{"key":"k","value":"v"}],"authorIfNew":"a"}"""
    assert(nullJson.as[PartitioningSubmitDTO].parentPartitioning.isEmpty)
    assert(parentJson.as[PartitioningSubmitDTO].parentPartitioning == Some(Seq(PartitionDTO("p", "v"))))
    assert(missingJson.as[PartitioningSubmitDTO].parentPartitioning.isEmpty)
  }

  "fromJson" should "ignore an unknown parentPartitionings key on the v1 DTO" in {
    val json =
      """{"partitioning":[{"key":"k","value":"v"}],"parentPartitionings":[[{"key":"x","value":"y"}]],"authorIfNew":"a"}"""
    assert(json.as[PartitioningSubmitDTO].parentPartitioning.isEmpty)
  }

  "asJsonString" should "serialize Seq[PartitionDTO] into json string" in {
    val partitionDTO = Seq(
      PartitionDTO("key1", "val1"),
      PartitionDTO("key2", "val2"),
      PartitionDTO("key3", "val3")
    )

    val expectedPartitionDTOJson =
      """[{"key":"key1","value":"val1"},{"key":"key2","value":"val2"},{"key":"key3","value":"val3"}]"""
    val actualPartitionDTOJson = partitionDTO.asJsonString

    assert(actualPartitionDTOJson == expectedPartitionDTOJson)
  }

  "fromJson" should "deserialize PartitioningDTO with parent partitioning from json string" in {
    val partitioningDTOJson =
      """
        |{
        |"partitioning":[{"key":"key","value":"val"}],
        |"parentPartitioning":[{"key":"parentKey","value":"parentVal"}],
        |"authorIfNew":"authorTest"
        |}
        |""".stripMargin

    val expectedPartitionDTO = PartitionDTO("key", "val")
    val expectedParentPartitionDTO = PartitionDTO("parentKey", "parentVal")
    val expectedPartitioningDTO = PartitioningSubmitDTO(
      partitioning = Seq(expectedPartitionDTO),
      parentPartitioning = Some(Seq(expectedParentPartitionDTO)),
      authorIfNew = "authorTest"
    )

    val actualPartitioningDTO = partitioningDTOJson.as[PartitioningSubmitDTO]

    assert(actualPartitioningDTO == expectedPartitioningDTO)
  }

  "asJsonString" should "encode PartitionDTO into string" in {
    val expected = "eyJrZXkiOiJrZXkxIiwidmFsdWUiOiJ2YWx1ZTEifQ=="
    val actual = PartitionDTO("key1", "value1").asBase64EncodedJsonString

    assert(actual == expected)
  }

  "asJsonString" should "encode AdditionalDataDTO into string" in {
    val expected = "eyJieVVzZXIiOiJBcnR1cml0byIsImRhdGEiOnsiTGEiOiJjYXNhIiwiZGUiOiJwYXBlbCJ9fQ=="
    val actual = AdditionalDataPatchDTO("Arturito", Map("La" -> "casa", "de" -> "papel")).asBase64EncodedJsonString

    assert(actual == expected)
  }

  private val v2Base = """"partitioning":[{"key":"k","value":"v"}],"author":"a""""

  "PartitioningSubmitV2DTO" should "decode literal v0.10.0 bodies with copyMeasureDefinitions = false" in {
    val bodies = Seq(
      s"""{$v2Base,"parentPartitioningId":null}""" -> Seq.empty[Long],
      s"""{$v2Base,"parentPartitioningId":5}""" -> Seq(5L),
      s"""{$v2Base,"parentPartitioningId":"5"}""" -> Seq(5L),
      s"""{$v2Base}""" -> Seq.empty[Long]
    )
    bodies.foreach { case (json, expectedIds) =>
      val actual = json.as[PartitioningSubmitV2DTO]
      assert(actual.parentPartitioningIds == expectedIds, json)
      assert(!actual.copyMeasureDefinitions, json)
    }
  }

  "PartitioningSubmitV2DTO" should "decode the copyMeasureDefinitions flag: absent, null, true, false" in {
    assert(!s"""{$v2Base}""".as[PartitioningSubmitV2DTO].copyMeasureDefinitions)
    assert(!s"""{$v2Base,"copyMeasureDefinitions":null}""".as[PartitioningSubmitV2DTO].copyMeasureDefinitions)
    assert(s"""{$v2Base,"copyMeasureDefinitions":true}""".as[PartitioningSubmitV2DTO].copyMeasureDefinitions)
    assert(!s"""{$v2Base,"copyMeasureDefinitions":false}""".as[PartitioningSubmitV2DTO].copyMeasureDefinitions)
  }

  "PartitioningSubmitV2DTO" should "reject a non-boolean copyMeasureDefinitions" in {
    assert(decode[PartitioningSubmitV2DTO](s"""{$v2Base,"copyMeasureDefinitions":"yes"}""").isLeft)
    assert(decode[PartitioningSubmitV2DTO](s"""{$v2Base,"copyMeasureDefinitions":"true"}""").isLeft)
    // sanity: the same base body without the bad flag decodes fine, so the failures above are caused by the flag
    assert(decode[PartitioningSubmitV2DTO](s"""{$v2Base}""").isRight)
  }

  "PartitioningSubmitV2DTO" should "reject a non-numeric legacy parentPartitioningId" in {
    assert(decode[PartitioningSubmitV2DTO](s"""{$v2Base,"parentPartitioningId":"abc"}""").isLeft)
    assert(decode[PartitioningSubmitV2DTO](s"""{$v2Base,"parentPartitioningId":5}""").isRight)
  }

  "PartitioningSubmitV2DTO" should "reject null or non-numeric elements in parentPartitioningIds" in {
    assert(decode[PartitioningSubmitV2DTO](s"""{$v2Base,"parentPartitioningIds":[1,null]}""").isLeft)
    assert(decode[PartitioningSubmitV2DTO](s"""{$v2Base,"parentPartitioningIds":["x"]}""").isLeft)
    assert(decode[PartitioningSubmitV2DTO](s"""{$v2Base,"parentPartitioningIds":[1,2]}""").isRight)
  }

  "PartitioningSubmitV2DTO" should "treat a null parentPartitioningIds as empty" in {
    assert(s"""{$v2Base,"parentPartitioningIds":null}""".as[PartitioningSubmitV2DTO].parentPartitioningIds.isEmpty)
  }

  "PartitioningSubmitV2DTO" should "de-duplicate ids within parentPartitioningIds, keeping first-seen order" in {
    val json = s"""{$v2Base,"parentPartitioningIds":[2,2,1,2,1]}"""
    assert(json.as[PartitioningSubmitV2DTO].parentPartitioningIds == Seq(2L, 1L))
  }

  "PartitioningSubmitV2DTO" should "merge legacy parentPartitioningId with parentPartitioningIds (legacy first, distinct)" in {
    val dedup = s"""{$v2Base,"parentPartitioningId":5,"parentPartitioningIds":[5,6]}"""
    val legacyFirst = s"""{$v2Base,"parentPartitioningId":7,"parentPartitioningIds":[3,4]}"""
    assert(dedup.as[PartitioningSubmitV2DTO].parentPartitioningIds == Seq(5L, 6L))
    assert(legacyFirst.as[PartitioningSubmitV2DTO].parentPartitioningIds == Seq(7L, 3L, 4L))
  }

  "PartitioningSubmitV2DTO" should "encode the flag and only the plural parent key (exact JSON)" in {
    val p = Seq(PartitionDTO("k", "v"))
    def expected(ids: String, flag: Boolean): String =
      s"""{"partitioning":[{"key":"k","value":"v"}],"parentPartitioningIds":$ids,""" +
        s""""author":"a","copyMeasureDefinitions":$flag}"""

    assert(PartitioningSubmitV2DTO(p, Seq(1L, 2L), "a").asJsonString == expected("[1,2]", flag = false))
    assert(
      PartitioningSubmitV2DTO(p, Seq(1L, 2L), "a", copyMeasureDefinitions = true).asJsonString ==
        expected("[1,2]", flag = true)
    )
    assert(PartitioningSubmitV2DTO(p, None, "a").asJsonString == expected("[]", flag = false))
  }

  "PartitioningSubmitV2DTO" should "round-trip and support the single-parent compat apply" in {
    val p = Seq(PartitionDTO("k", "v"))
    for {
      ids <- Seq(Seq.empty[Long], Seq(1L), Seq(1L, 2L, 3L))
      flag <- Seq(false, true)
    } {
      val dto = PartitioningSubmitV2DTO(p, ids, "a", flag)
      assert(dto.asJsonString.as[PartitioningSubmitV2DTO] == dto)
    }
    val single = PartitioningSubmitV2DTO(p, Some(7L), "a")
    assert(single.parentPartitioningIds == Seq(7L))
    assert(!single.copyMeasureDefinitions)
    assert(PartitioningSubmitV2DTO(partitioning = p, parentPartitioningId = None, author = "a")
      .parentPartitioningIds.isEmpty)
  }

}
