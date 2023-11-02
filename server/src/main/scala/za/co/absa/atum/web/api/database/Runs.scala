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

package za.co.absa.atum.web.api.database

import slick.jdbc.{GetResult, SQLActionBuilder}
import za.co.absa.atum.model.dto.CheckpointDTO
import za.co.absa.fadb.DBFunction._
import za.co.absa.fadb.DBSchema
import za.co.absa.fadb.naming.implementations.SnakeCaseNaming.Implicits._
import za.co.absa.fadb.slick.FaDbPostgresProfile.api._
import za.co.absa.fadb.slick.{SlickFunctionWithStatusSupport, SlickPgEngine}
import za.co.absa.fadb.status.handling.implementations.StandardStatusHandling

class Runs (implicit dBEngine: SlickPgEngine) extends DBSchema{
  import Runs._

  val writeCheckpoint = new WriteCheckpoint
}

object Runs {

  class WriteCheckpoint(implicit override val schema: DBSchema, override val dbEngine: SlickPgEngine)
    extends DBSingleResultFunction[CheckpointDTO, Unit, SlickPgEngine]
      with SlickFunctionWithStatusSupport[CheckpointDTO, Unit]
      with StandardStatusHandling
  {

    /** Call the database function that create a checkpoint to the db **/
    override protected def sql(values: CheckpointDTO): SQLActionBuilder = {
      val measureNames = values.measurements.map(_.measure.measureName)
      val controlColumns = values.measurements.map(_.measure.controlColumns)
      val measureResults = values.measurements.map(_.result)
      // val measureResultsAsJson = JacksonHelper.objectMapper.writeValueAsString(measureResults)

      // ToDo serialize the partitioning and measurement columns into JSON object, #71
      // $measureNames::TEXT[],
      // $controlColumns::TEXT[],
      sql"""SELECT #$selectEntry
            FROM #$functionName(
              '{}'::JSONB,
              ${values.id},
              ${values.name},
              ${values.processStartTime},
              ${values.processEndTime},
              '{}'::JSONB,
              ${values.author}
            ) #$alias;"""
    }

    override protected def slickConverter: GetResult[Unit] = GetResult { _ => }
  }
}
