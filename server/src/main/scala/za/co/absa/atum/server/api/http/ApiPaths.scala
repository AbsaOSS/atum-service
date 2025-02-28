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

package za.co.absa.atum.server.api.http

object ApiPaths {

  final val Api = "api"
  final val V1 = "v1"
  final val V2 = "v2"

  final val Health = "health"
  final val ZioMetrics = "zio-metrics"

  object V1Paths {

    final val CreateCheckpoint = "createCheckpoint"
    final val CreatePartitioning = "createPartitioning"

  }

  object V2Paths {

    final val Partitionings = "partitionings"
    final val Checkpoints = "checkpoints"
    final val AdditionalData = "additional-data"
    final val Flows = "flows"
    final val Measures = "measures"
    final val MainFlow = "main-flow"
    final val Ancestors = "ancestors"

  }

}
