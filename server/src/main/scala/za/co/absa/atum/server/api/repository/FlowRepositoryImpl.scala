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

package za.co.absa.atum.server.api.repository

import za.co.absa.atum.model.dto.CheckpointQueryDTO
import za.co.absa.atum.server.api.database.flows.functions.GetFlowCheckpoints
import za.co.absa.atum.server.api.exception.DatabaseError
import za.co.absa.atum.server.model.CheckpointFromDB
import zio._

class FlowRepositoryImpl(getFlowCheckpointsFn: GetFlowCheckpoints) extends FlowRepository with BaseRepository {

  override def getFlowCheckpoints(checkpointQueryDTO: CheckpointQueryDTO): IO[DatabaseError, Seq[CheckpointFromDB]] = {
    dbCall(getFlowCheckpointsFn(checkpointQueryDTO), "getFlowCheckpoints")
  }

}

object FlowRepositoryImpl {
  val layer: URLayer[GetFlowCheckpoints, FlowRepository] = ZLayer {
    for {
      getFlowCheckpoints <- ZIO.service[GetFlowCheckpoints]
    } yield new FlowRepositoryImpl(getFlowCheckpoints)
  }
}
