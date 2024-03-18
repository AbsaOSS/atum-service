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

import za.co.absa.atum.server.api.controller.{CheckpointController, PartitioningController}
import zio.RIO
import zio.metrics.connectors.prometheus.PrometheusPublisher

object HttpEnv {

  type Env = PartitioningController with CheckpointController with PrometheusPublisher
  // naming effect types as `F` is a convention in Scala community
  type F[A] = RIO[Env, A]

}
