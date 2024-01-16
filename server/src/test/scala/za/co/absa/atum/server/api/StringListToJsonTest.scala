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
