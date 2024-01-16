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

import org.scalatest.funsuite.AnyFunSuiteLike
import spray.json.DefaultJsonProtocol.{RootJsObjectFormat, listFormat}
import spray.json.{JsObject, JsString, JsValue}

class StringListToJsonTest extends AnyFunSuiteLike {

  test("testConvertToJsonValue") {
    val stringSeq: Seq[String] = Seq(
      """{"key1": "value1"}""",
      """{"key2": "value2"}""",
      """{"key3": "value3"}"""
    )

    val ExpectedResults: List[JsValue] = List(
      JsObject("key1" -> JsString("value1")),
      JsObject("key2" -> JsString("value2")),
      JsObject("key3" -> JsString("value3"))
    )

    val jsValuesList: List[JsValue] = StringListToJson.convertToJsonValue(stringSeq)

    assert(ExpectedResults == jsValuesList)
  }

}
