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

import org.scalatest.funsuite.AnyFunSuiteLike
import za.co.absa.atum.model.dto.{FlowDTO, PartitionDTO}
import za.co.absa.atum.model.utils.JsonSyntaxExtensions.JsonDeserializationSyntax

class JsonDeserializationSyntaxUnitTests extends AnyFunSuiteLike {
  test("Decode object from Json with defined Option field") {
    val source =
      """{
        |  "id": 1,
        |  "name": "Test flow",
        |  "description": "Having description",
        |  "fromPattern": false
        |}""".stripMargin
    val result = source.as[FlowDTO]
    val expected = FlowDTO(
      id = 1,
      name = "Test flow",
      description = Some("Having description"),
      fromPattern = false
    )
    assert(result == expected)
  }

  test("Decode object from Json with Option field undefined") {
    val source =
      """{
        |  "id": 1,
        |  "name": "Test flow",
        |  "fromPattern": true
        |}""".stripMargin
    val result = source.as[FlowDTO]
    val expected = FlowDTO(
      id = 1,
      name = "Test flow",
      description = None,
      fromPattern = true
    )
    assert(result == expected)
  }

  test("Fail when input is not Json") {
    val source = "This is not a Json!"
    intercept[io.circe.Error] {
      source.as[FlowDTO]
    }
  }

  test("Fail when given wrong class") {
    val source =
      """{
        |  "id": 1,
        |  "name": "Test flow",
        |  "description": "Having description",
        |  "fromPattern": false
        |}""".stripMargin
    intercept[io.circe.Error] {
      source.as[PartitionDTO]
    }
  }


  test("Decode object from Base64 string") {
    val source = "eyJpZCI6MSwibmFtZSI6IlRlc3QgZmxvdyIsImRlc2NyaXB0aW9uIjpudWxsLCJmcm9tUGF0dGVybiI6ZmFsc2V9"
    val result = source.fromBase64As[FlowDTO]
    val expected = FlowDTO(
      id = 1,
      name = "Test flow",
      description = None,
      fromPattern = false
    )
    assert(result == Right(expected))
  }

  test("Failing decode if not Base64 string") {
    val source = ""
    val result = source.fromBase64As[FlowDTO]
    assert(result.isLeft)
  }

}
