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

package za.co.absa.atum.web.api.repositories

import za.co.absa.atum.model.dto.CheckpointDTO

//import slick.jdbc.{GetResult, PositionedResult, SQLActionBuilder}
//import za.co.absa.fadb.DBFunction._
//import za.co.absa.fadb.DBSchema
//import za.co.absa.fadb.slick.{SlickFunction, SlickFunctionWithStatusSupport, SlickPgEngine}
//import za.co.absa.fadb.status.handling.implementations.StandardStatusHandling
//import za.co.absa.fadb.naming.implementations.SnakeCaseNaming.Implicits._

class Checkpoint (implicit dBEngine: SlickPgEngine) extends DBSchema{
  import CheckpointDTO._
  val createCheckpoint = new CreateCheckpoint
}

class CreateCheckpoint //extends
//  DBSingleResultFunction[CheckpointDTO, Unit, SlickPgEngine]
//  with SlickFunctionWithStatusSupport[CheckpointDTO, Unit]
//  with StandardStatusHandling
{

//  private val jdbcTemplate: JdbcTemplate = null
//  def createCheckout(checkpoint: CheckpointDTO): Unit = {
//    jdbcTemplate.execute(
//      "SELECT runs.open_checkpoint(?, ?, ?, ?, ?, ?)",
//      checkpoint.id,
//      checkpoint.name,
//      checkpoint.author,
//      checkpoint.partitioning,
//      checkpoint.processStartTime,
//      checkpoint.processEndTime,
//      checkpoint.measurements
//    )
//  }

  override protected def sql(values: CheckpointDTO): SQLActionBuilder = {
    val about = JacksonHelper.objectMapper.writeValueAsString(values.about)
    val checkpointData = JacksonHelper.objectMapper.writeValueAsString(values)
    sql"""SELECT #$selectEntry
            FROM #$functionName(
              ${values.id},
              ${values.name},
              ${values.author},
              ${values.partitioning},
              ${values.processStartTime},
              ${values.processEndTime},
              ${values.measurements},
              $about::JSONB,
              $checkpointData::JSONB
            ) #$alias;"""
  }

  override protected def slickConverter: GetResult[Unit] = GetResult { _ => }
}
