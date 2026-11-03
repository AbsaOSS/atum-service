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

package za.co.absa.atum.server.api.v2.http

import io.circe.{ACursor, Json}
import io.circe.syntax._
import sttp.apispec.openapi.circe._
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import za.co.absa.atum.model.ApiPaths
import zio.Scope
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}

/**
 *  Pins the OpenAPI contract of the consolidated `POST /api/v2/partitionings`: documented statuses, the `Location`
 *  header and the identical body on 200/201, and the request flag `copyMeasureDefinitions` (optional, default false).
 */
object OpenApiPostPartitioningUnitTests extends ZIOSpecDefault {

  private val PostPath = s"/${ApiPaths.Api}/${ApiPaths.V2}/${ApiPaths.V2Paths.Partitionings}"
  private val RequestSchemaName = "PartitioningSubmitV2DTO"

  private val openApi: Json =
    OpenAPIDocsInterpreter().toOpenAPI(List(Endpoints.postPartitioningEndpoint), "Atum API", "1.0").asJson

  private val operation: ACursor = openApi.hcursor.downField("paths").downField(PostPath).downField("post")
  private def response(code: String): ACursor = operation.downField("responses").downField(code)
  private def jsonSchemaOf(code: String): Option[Json] =
    response(code).downField("content").downField("application/json").downField("schema").focus

  private val requestSchema: ACursor =
    openApi.hcursor.downField("components").downField("schemas").downField(RequestSchemaName)
  private val requiredOfRequest: Set[String] =
    requestSchema.downField("required").as[List[String]].getOrElse(List.empty).toSet

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("OpenApiPostPartitioningSuite")(
    test("the POST operation is documented on /api/v2/partitionings and is not deprecated") {
      assertTrue(operation.focus.isDefined) &&
      assertTrue(!operation.downField("deprecated").as[Boolean].getOrElse(false))
    },
    test("responses document 200, 201, 400, 404 and 409") {
      val codes = operation.downField("responses").keys.map(_.toList).getOrElse(List.empty)
      assertTrue(Set("200", "201", "400", "404", "409").subsetOf(codes.toSet))
    },
    test("200 and 201 both document the Location header") {
      assertTrue(response("200").downField("headers").downField("Location").focus.isDefined) &&
      assertTrue(response("201").downField("headers").downField("Location").focus.isDefined)
    },
    test("200 and 201 document the same JSON body schema") {
      assertTrue(jsonSchemaOf("200").isDefined) &&
      assertTrue(jsonSchemaOf("200") == jsonSchemaOf("201"))
    },
    test("200 and 201 carry distinct descriptions (created vs already existed)") {
      val d200 = response("200").downField("description").as[String]
      val d201 = response("201").downField("description").as[String]
      assertTrue(d200.isRight) && assertTrue(d201.isRight) && assertTrue(d200 != d201)
    },
    test("the request body references the PartitioningSubmitV2DTO schema") {
      val ref = operation
        .downField("requestBody")
        .downField("content")
        .downField("application/json")
        .downField("schema")
        .downField("$ref")
        .as[String]
      assertTrue(ref == Right(s"#/components/schemas/$RequestSchemaName"))
    },
    test("copyMeasureDefinitions is not required in the request schema") {
      assertTrue(requestSchema.focus.isDefined) &&
      assertTrue(!requiredOfRequest.contains("copyMeasureDefinitions")) &&
      assertTrue(requestSchema.downField("properties").downField("copyMeasureDefinitions").focus.isDefined)
    },
    test("copyMeasureDefinitions documents default false") {
      val default = requestSchema
        .downField("properties")
        .downField("copyMeasureDefinitions")
        .downField("default")
        .as[Boolean]
      assertTrue(default == Right(false))
    },
    test("partitioning and author stay required in the request schema") {
      assertTrue(requiredOfRequest.contains("partitioning")) &&
      assertTrue(requiredOfRequest.contains("author"))
    },
    test("the endpoint description documents the 100-distinct-parents cap (400) and the legacy parent key") {
      val description = operation.downField("description").as[String].getOrElse("")
      assertTrue(
        description.contains("At most 100 distinct parent ids"),
        description.contains("400"),
        description.contains("`parentPartitioningId`"),
        description.contains("`Location` header is returned on 200 as well as on 201")
      )
    },
    test("parentPartitioningIds documents the cap, de-duplication and the legacy parentPartitioningId key") {
      val description = requestSchema
        .downField("properties")
        .downField("parentPartitioningIds")
        .downField("description")
        .as[String]
        .getOrElse("")
      assertTrue(
        description.contains("at most 100 distinct ids"),
        description.contains("duplicates are ignored"),
        description.contains("`parentPartitioningId`"),
        description.contains("numeric string")
      )
    },
    test("the cap is documented in text only: no maxItems, so the controller's BadRequest envelope stays the answer") {
      // a Tapir maxSize validator would be enforced by the interpreter with its own (non-envelope) 400 body
      val maxItems = requestSchema.downField("properties").downField("parentPartitioningIds").downField("maxItems")
      assertTrue(maxItems.focus.isEmpty)
    }
  )
}
