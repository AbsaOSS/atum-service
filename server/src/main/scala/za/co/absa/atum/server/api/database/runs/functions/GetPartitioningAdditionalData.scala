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

import doobie.Fragment
import doobie.implicits.toSqlInterpolator
import doobie.util.Read
import za.co.absa.atum.model.dto.PartitioningDTO
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.api.database.runs.Runs
import za.co.absa.atum.server.model.PartitioningForDB
import za.co.absa.db.fadb.DBSchema
import za.co.absa.db.fadb.doobie.DoobieFunction.DoobieMultipleResultFunctionWithAggStatus
import za.co.absa.db.fadb.doobie.DoobieEngine
import zio.interop.catz.asyncInstance
import zio.{Task, URLayer, ZIO, ZLayer}
import io.circe.syntax._
import za.co.absa.atum.server.api.database.DoobieImplicits.getMapWithOptionStringValues
import doobie.postgres.circe.jsonb.implicits.jsonbPut

class GetPartitioningAdditionalData (implicit schema: DBSchema, dbEngine: DoobieEngine[Task])
  extends DoobieMultipleResultFunctionWithAggStatus[PartitioningDTO, (String, Option[String]), Task](values => Seq(fr"$values"))
  {

    override val fieldsToSelect: Seq[String] = Seq("status", "status_text", "ad_name", "ad_value")

    override def sql(values: PartitioningDTO)(implicit read: Read[(String, Option[String])]): Fragment = {
    val partitioning: PartitioningForDB = PartitioningForDB.fromSeqPartitionDTO(values)
    val partitioningJson = partitioning.asJson

    sql"""SELECT ${Fragment.const(selectEntry)} FROM ${Fragment.const(functionName)}(
                  $partitioningJson
                ) ${Fragment.const(alias)};"""
  }

}

object GetPartitioningAdditionalData {
  val layer: URLayer[PostgresDatabaseProvider, GetPartitioningAdditionalData] = ZLayer {
    for {
      dbProvider <- ZIO.service[PostgresDatabaseProvider]
    } yield new GetPartitioningAdditionalData()(Runs, dbProvider.dbEngine)
  }
}
