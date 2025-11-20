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

package za.co.absa.atum.server.api.database.runs.functions

import za.co.absa.db.fadb.DBSchema
import za.co.absa.db.fadb.doobie.DoobieEngine
import za.co.absa.db.fadb.doobie.DoobieFunction.DoobieMultipleResultFunctionWithAggStatus
import doobie.implicits.toSqlInterpolator
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.api.database.runs.Runs
import za.co.absa.atum.server.api.database.runs.functions.GetCheckpointProperties._
import za.co.absa.db.fadb.status.aggregation.implementations.ByFirstRowStatusAggregator
import za.co.absa.db.fadb.status.handling.implementations.StandardStatusHandling
import java.util.UUID
import doobie.postgres.implicits._
import zio._

class GetCheckpointProperties(implicit schema: DBSchema, dbEngine: DoobieEngine[Task])
  extends DoobieMultipleResultFunctionWithAggStatus[GetCheckpointPropertiesArgs, GetCheckpointPropertiesResult, Task](
    args => Seq(fr"${args.checkpointId}")
  ) with StandardStatusHandling with ByFirstRowStatusAggregator {

  override def fieldsToSelect: Seq[String] = super.fieldsToSelect ++ Seq("property_name", "property_value")
}

object GetCheckpointProperties {
  case class GetCheckpointPropertiesArgs(checkpointId: UUID)
  case class GetCheckpointPropertiesResult(propertyName: String, propertyValue: String)

  object GetCheckpointPropertiesResult {
    def toMapOption(props: Seq[GetCheckpointPropertiesResult]): Option[Map[String, String]] = {
      val map = props.map(p => p.propertyName -> p.propertyValue).toMap
      if (map.nonEmpty) Some(map) else None
    }
  }

  val layer: URLayer[PostgresDatabaseProvider, GetCheckpointProperties] = ZLayer {
    for {
      dbProvider <- ZIO.service[PostgresDatabaseProvider]
    } yield new GetCheckpointProperties()(Runs, dbProvider.dbEngine)
  }
}
