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

package za.co.absa.atum.server.api.common.http

import sttp.apispec.openapi.{Operation, PathItem}
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import za.co.absa.atum.model.ApiPaths
import zio.Scope
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}

object OpenApiDeprecationUnitTests extends ZIOSpecDefault {

  private val V1Prefix = s"/${ApiPaths.Api}/${ApiPaths.V1}/"
  private val V2Prefix = s"/${ApiPaths.Api}/${ApiPaths.V2}/"

  private def operationsOf(item: PathItem): List[Operation] =
    List(item.get, item.put, item.post, item.delete, item.options, item.head, item.patch, item.trace).flatten

  private def methodsOf(item: PathItem): List[String] =
    List(
      item.get.map(_ => "GET"),
      item.put.map(_ => "PUT"),
      item.post.map(_ => "POST"),
      item.delete.map(_ => "DELETE"),
      item.options.map(_ => "OPTIONS"),
      item.head.map(_ => "HEAD"),
      item.patch.map(_ => "PATCH"),
      item.trace.map(_ => "TRACE")
    ).flatten

  /**
   *  Independently written (hand-maintained on purpose) list of every operation the server is expected to serve and
   *  document. Adding or removing an endpoint must be reflected here, otherwise the drift test below fails.
   *  The literals deliberately do not use `ApiPaths`, so a wrong constant there cannot cancel itself out.
   */
  private val expectedOperations: List[String] = List(
    "POST /api/v1/createCheckpoint",
    "POST /api/v1/createPartitioning",
    "POST /api/v2/partitionings/{partitioningId}/checkpoints",
    "POST /api/v2/partitionings",
    "GET /api/v2/partitionings/{partitioningId}/additional-data",
    "PATCH /api/v2/partitionings/{partitioningId}/additional-data",
    "GET /api/v2/partitionings",
    "GET /api/v2/partitionings/{partitioningId}/checkpoints/{checkpointId}",
    "GET /api/v2/partitionings/{partitioningId}/checkpoints",
    "GET /api/v2/flows/{flowId}/checkpoints",
    "GET /api/v2/partitionings/{partitioningId}",
    "GET /api/v2/partitionings/{partitioningId}/measures",
    "GET /api/v2/flows/{flowId}/partitionings",
    "GET /api/v2/partitionings/{partitioningId}/main-flow",
    "PATCH /api/v2/partitionings/{partitioningId}/ancestors",
    "GET /api/v2/partitionings/{partitioningId}/ancestors",
    "GET /health",
    "GET /health/readiness",
    "GET /health/liveness",
    "GET /build-info"
  )

  private val openApi = OpenAPIDocsInterpreter().toOpenAPI(Routes.swaggerEndpoints, "Atum API", "1.0")

  private val operationsByPath: List[(String, Operation)] =
    openApi.paths.pathItems.toList.flatMap { case (path, item) => operationsOf(item).map(path -> _) }

  private val documentedOperations: List[String] =
    openApi.paths.pathItems.toList.flatMap { case (path, item) => methodsOf(item).map(method => s"$method $path") }

  private val v1Ops = operationsByPath.filter(_._1.startsWith(V1Prefix))
  private val v2Ops = operationsByPath.filter(_._1.startsWith(V2Prefix))
  private val otherOps =
    operationsByPath.filterNot { case (p, _) => p.startsWith(V1Prefix) || p.startsWith(V2Prefix) }

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("OpenApiDeprecationSuite")(
    test("swagger documents exactly the independently listed operations (no drift, no collisions)") {
      val missing = expectedOperations.diff(documentedOperations)
      val unexpected = documentedOperations.diff(expectedOperations)
      assertTrue(
        missing.isEmpty,
        unexpected.isEmpty,
        documentedOperations.distinct.size == documentedOperations.size,
        expectedOperations.distinct.size == expectedOperations.size
      )
    },
    test("GET /api/v2/partitionings/{partitioningId} is documented and not deprecated") {
      val getById = openApi.paths.pathItems
        .get(s"${V2Prefix}${ApiPaths.V2Paths.Partitionings}/{partitioningId}")
        .flatMap(_.get)
      assertTrue(getById.isDefined) && assertTrue(!getById.exists(_.deprecated.contains(true)))
    },
    test("every v1 operation is marked deprecated: true") {
      assertTrue(v1Ops.nonEmpty) && assertTrue(v1Ops.forall(_._2.deprecated.contains(true)))
    },
    test("no v2 operation is marked deprecated") {
      assertTrue(v2Ops.nonEmpty) && assertTrue(v2Ops.forall(!_._2.deprecated.contains(true)))
    },
    test("no common (health/readiness/liveness/build-info) operation is marked deprecated") {
      assertTrue(otherOps.nonEmpty) && assertTrue(otherOps.forall(!_._2.deprecated.contains(true)))
    },
    test("v1 operations point to the v2 replacement in their description") {
      assertTrue(v1Ops.forall(_._2.description.exists(_.contains("API v2"))))
    }
  )
}
