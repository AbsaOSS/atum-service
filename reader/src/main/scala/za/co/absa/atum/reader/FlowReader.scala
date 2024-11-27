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

package za.co.absa.atum.reader

import sttp.client3.SttpBackend
import sttp.monad.MonadError
import za.co.absa.atum.model.types.basic.AtumPartitions
import za.co.absa.atum.reader.basic.{PartitioningIdProvider, Reader}
import za.co.absa.atum.reader.server.ServerConfig

/**
 * This class is a reader that reads data tight to a flow.
 * @param mainFlowPartitioning  - the partitioning of the main flow; renamed from ancestor's 'flowPartitioning'
 * @param serverConfig          - tha Atum server configuration
 * @param backend               - sttp backend, that will be executing the requests
 * @param ev                    - using evidence based approach to ensure that the type F is a MonadError instead of using context
 *                              bounds, as it make the imports easier to follow
 * @tparam F                    - the effect type (e.g. Future, IO, Task, etc.)
 */
class FlowReader[F[_]](val mainFlowPartitioning: AtumPartitions)
                      (implicit serverConfig: ServerConfig, backend: SttpBackend[F, Any], ev: MonadError[F])
  extends Reader[F] with PartitioningIdProvider[F]{

  override def partitioning: AtumPartitions = mainFlowPartitioning

}
