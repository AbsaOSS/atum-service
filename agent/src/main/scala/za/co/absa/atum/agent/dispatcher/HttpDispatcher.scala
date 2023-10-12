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

package za.co.absa.atum.agent.dispatcher

import com.typesafe.config.Config
import org.apache.spark.internal.Logging
import sttp.client3._
import sttp.model.Uri
import za.co.absa.atum.model.Partitioning
import za.co.absa.atum.model.dto.{AtumContextDTO, CheckpointDTO, PartitioningDTO}

class HttpDispatcher(config: Config) extends Dispatcher with Logging {

  private val serverUri = Uri.unsafeParse(config.getString("url"))
  private val backend = HttpURLConnectionBackend()

  logInfo("using http dispatcher")
  logInfo(s"serverUri $serverUri")

  override def fetchAtumContext(
    partitioning: Partitioning,
    parentPartitioning: Option[Partitioning]
  ): Option[AtumContextDTO] = {
    val partitioningDTO = PartitioningDTO(partitioning, parentPartitioning)
    basicRequest
      .body(s"$partitioningDTO")
      .get(serverUri)
      .send(backend)

    None // todo: implement request
  }

  override def saveCheckpoint(checkpoint: CheckpointDTO): Unit = {
    basicRequest
      .body(s"$checkpoint")
      .post(serverUri)
      .send(backend)
  }
}
