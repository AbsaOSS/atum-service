/*
 * Copyright 2024 ABSA Group Limited
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
import za.co.absa.atum.model.dto.FlowDTO
import za.co.absa.atum.model.utils.JsonSyntaxExtensions.JsonSerializationSyntax

class JsonSerializationSyntaxUnitTests extends AnyFunSuiteLike {
  test("Converting to Json with option field defined") {
    val expected = """{"id":1,"name":"Test flow","description":"Having description","fromPattern":false}"""
    val result = FlowDTO(
      id = 1,
      name = "Test flow",
      description = Some("Having description"),
      fromPattern = false
    ).asJsonString
    assert(result == expected)
  }

  test("Converting to Json with option field undefined") {
    val expected = """{"id":1,"name":"Test flow","description":null,"fromPattern":true}"""
    val result = FlowDTO(
      id = 1,
      name = "Test flow",
      description = None,
      fromPattern = true
    ).asJsonString
    assert(result == expected)
  }

  test("Converting to Base64") {
    val expected = "eyJpZCI6MSwibmFtZSI6IlRlc3QgZmxvdyIsImRlc2NyaXB0aW9uIjpudWxsLCJmcm9tUGF0dGVybiI6ZmFsc2V9"
    val result = FlowDTO(
      id = 1,
      name = "Test flow",
      description = None,
      fromPattern = false
    ).asBase64EncodedJsonString
    assert(result == expected)
  }

}
