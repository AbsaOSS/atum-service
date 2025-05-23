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

import za.co.absa.atum.server.api.v1.controller.{
  CheckpointController => CheckpointControllerV1,
  PartitioningController => PartitioningControllerV1
}
import za.co.absa.atum.server.api.v2.controller.{
  CheckpointController => CheckpointControllerV2,
  PartitioningController => PartitioningControllerV2
}
import za.co.absa.atum.server.api.v2.controller.{FlowController => FlowControllerV2}
import zio.RIO
import zio.metrics.connectors.prometheus.PrometheusPublisher

object HttpEnv {

  type Env =
    PartitioningControllerV1
      with CheckpointControllerV1
      with PartitioningControllerV2
      with CheckpointControllerV2
      with FlowControllerV2
      with PrometheusPublisher

  // naming effect types as `F` is a convention in Scala community
  type F[A] = RIO[Env, A]

}
