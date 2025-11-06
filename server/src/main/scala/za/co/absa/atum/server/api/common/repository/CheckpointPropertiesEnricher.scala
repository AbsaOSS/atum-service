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

package za.co.absa.atum.server.api.common.repository

import za.co.absa.atum.model.dto.CheckpointWithProperties
import za.co.absa.atum.server.api.database.runs.functions.GetCheckpointProperties
import za.co.absa.atum.server.api.database.runs.functions.GetCheckpointProperties._
import za.co.absa.atum.server.api.exception.DatabaseError
import zio.ZIO
import zio.interop.catz._

trait CheckpointPropertiesEnricher { this: BaseRepository =>

  def getCheckpointPropertiesFn: GetCheckpointProperties

  protected def enrichWithProperties[T](checkpoint: CheckpointWithProperties[T]): ZIO[Any, DatabaseError, T] = {
    dbMultipleResultCallWithAggregatedStatus(
      getCheckpointPropertiesFn(GetCheckpointPropertiesArgs(checkpoint.id)),
      "getCheckpointProperties"
    ).map { checkpointProperties =>
      val props = GetCheckpointPropertiesResult.toMapOption(checkpointProperties)
      checkpoint.withProperties(props)
    }
  }

}
