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

import doobie.implicits.toSqlInterpolator
import doobie.postgres.implicits._
import io.circe.syntax.EncoderOps
import za.co.absa.atum.model.dto.PartitioningSubmitV2DTO
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.api.database.runs.Runs
import za.co.absa.atum.server.model.database.PartitioningForDB
import za.co.absa.db.fadb.DBSchema
import za.co.absa.db.fadb.doobie.DoobieEngine
import za.co.absa.db.fadb.doobie.DoobieFunction.DoobieSingleResultFunctionWithStatus
import za.co.absa.db.fadb.doobie.postgres.circe.implicits.jsonbPut
import za.co.absa.db.fadb.exceptions._
import za.co.absa.db.fadb.status.FunctionStatus
import za.co.absa.db.fadb.status.handling.StatusHandling
import zio._

/**
 *  Wrapper of `runs.create_partitioning_with_parents(JSONB, TEXT, BIGINT[])` used for 2+ parents.
 *  Statuses: 11-14 OK, 41 parent not found, 43 cycle detected (mapped to conflict).
 */
class CreatePartitioningWithParents(implicit schema: DBSchema, dbEngine: DoobieEngine[Task])
  extends DoobieSingleResultFunctionWithStatus[PartitioningSubmitV2DTO, Long, Task](partitioningSubmit =>
    Seq(
      fr"${PartitioningForDB.fromSeqPartitionDTO(partitioningSubmit.partitioning).asJson}",
      fr"${partitioningSubmit.author}",
      fr"${partitioningSubmit.parentPartitioningIds.toList}::BIGINT[]"
    )
  )
    with StatusHandling {

  // `skipped_ad_keys` is selected for completeness but not read (the Doobie Read yields only the Long id);
  // surfacing it is deferred.
  override def fieldsToSelect: Seq[String] = super.fieldsToSelect ++ Seq("id_partitioning", "skipped_ad_keys")

  override def checkStatus(functionStatus: FunctionStatus): Option[StatusException] =
    functionStatus.statusCode match {
      case 43                  => Some(DataConflictException(functionStatus))
      case code if code / 10 == 1 => None
      case code if code / 10 == 3 => Some(DataConflictException(functionStatus))
      case code if code / 10 == 4 => Some(DataNotFoundException(functionStatus))
      case code if code / 10 == 2 => Some(ServerMisconfigurationException(functionStatus))
      case code if Set(5, 6, 7, 8).contains(code / 10) => Some(ErrorInDataException(functionStatus))
      case code if code / 10 == 9 => Some(OtherStatusException(functionStatus))
      case _                   => Some(StatusOutOfRangeException(functionStatus))
    }
}

object CreatePartitioningWithParents {
  val layer: URLayer[PostgresDatabaseProvider, CreatePartitioningWithParents] = ZLayer {
    for {
      dbProvider <- ZIO.service[PostgresDatabaseProvider]
    } yield new CreatePartitioningWithParents()(Runs, dbProvider.dbEngine)
  }
}
