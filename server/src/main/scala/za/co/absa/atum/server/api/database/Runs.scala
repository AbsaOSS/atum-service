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

<<<<<<< HEAD:server/src/main/scala/za/co/absa/atum/web/api/database/Runs.scala
package za.co.absa.atum.web.api.database

import za.co.absa.fadb.slick.FaDbPostgresProfile.api._
import slick.jdbc.{GetResult, SQLActionBuilder}
import za.co.absa.atum.model.dto.CheckpointDTO
import za.co.absa.atum.web.api.database.Runs.WriteCheckpoint
import za.co.absa.fadb.DBFunction._
import za.co.absa.fadb.DBSchema
import za.co.absa.fadb.naming.implementations.SnakeCaseNaming.Implicits._
import za.co.absa.fadb.slick.{SlickFunctionWithStatusSupport, SlickPgEngine}
import za.co.absa.fadb.status.handling.implementations.StandardStatusHandling

/**
 *
 * @param dBEngine
 */
class Runs (implicit dBEngine: SlickPgEngine) extends DBSchema{
=======
package za.co.absa.atum.server.api.database

import slick.jdbc.{GetResult, SQLActionBuilder}
import za.co.absa.atum.model.dto.CheckpointDTO
import za.co.absa.atum.model.utils.SerializationUtils
import za.co.absa.atum.server.model.PartitioningForDB
import za.co.absa.fadb.DBFunction._
import za.co.absa.fadb.DBSchema
import za.co.absa.fadb.naming.implementations.SnakeCaseNaming.Implicits._
import za.co.absa.fadb.slick.FaDbPostgresProfile.api._
import za.co.absa.fadb.slick.{SlickFunctionWithStatusSupport, SlickPgEngine}
import za.co.absa.fadb.status.handling.implementations.StandardStatusHandling

class Runs (implicit dBEngine: SlickPgEngine) extends DBSchema{
  import Runs._
>>>>>>> origin:server/src/main/scala/za/co/absa/atum/server/api/database/Runs.scala

  val writeCheckpoint = new WriteCheckpoint
}

object Runs {

<<<<<<< HEAD:server/src/main/scala/za/co/absa/atum/web/api/database/Runs.scala
  /**
   *
   * @param schema
   * @param dbEngine
   */
  class WriteCheckpoint(implicit override val schema: DBSchema, override val dbEngine: SlickPgEngine) extends
    DBSingleResultFunction[CheckpointDTO, Unit, SlickPgEngine]
    with SlickFunctionWithStatusSupport[CheckpointDTO, Unit]
    with StandardStatusHandling
  {

    /** Call the database function that create a checkpoint to the db
     *
     * @param values
     * @return
     */
    override protected def sql(values: CheckpointDTO): SQLActionBuilder = {

      // ToDo serialize the partitioning and measurement columns into JSON object, #71
      sql"""SELECT #$selectEntry
            FROM #$functionName(
              ${values.id},
              ${values.name},
              ${values.author},
              '{}'::JSON,
              ${values.processStartTime},
              ${values.processEndTime},
              '{}'::JSON
            ) #$alias;"""
    }

    /**
     *
     * @return
     */
=======
  private def scalaIterableToSQLArray(toConvert: Seq[Any]): String = {
    SerializationUtils.asJson(toConvert)
      .replace("[", "{")
      .replace("]", "}")
  }

  class WriteCheckpoint(implicit override val schema: DBSchema, override val dbEngine: SlickPgEngine)
    extends DBSingleResultFunction[CheckpointDTO, Unit, SlickPgEngine]
      with SlickFunctionWithStatusSupport[CheckpointDTO, Unit]
      with StandardStatusHandling
  {

    /** Call the database function that create a checkpoint to the db **/
    override protected def sql(values: CheckpointDTO): SQLActionBuilder = {
      val partitioning = PartitioningForDB.fromSeqPartitionDTO(values.partitioning)
      val partitioningNormalized = SerializationUtils.asJson(partitioning)

      val measureNames = scalaIterableToSQLArray(values.measurements.map(_.measure.measureName))
      val controlColumns = scalaIterableToSQLArray(values.measurements.map(_.measure.controlColumns))

      val measureResults = values.measurements.map(_.result)
      val measureResultsAsJsonString = SerializationUtils.asJson(measureResults)
        .replace("[", "{")
        .replace("]", "}")
      println(measureResults)
      println(SerializationUtils.asJson(measureResults))
      println(measureResultsAsJsonString)

      sql"""SELECT #$selectEntry
            FROM #$functionName(
              $partitioningNormalized::JSONB,
              ${values.id},
              ${values.name},
              ${values.processStartTime}::TIMESTAMPTZ,
              ${values.processEndTime}::TIMESTAMPTZ,
              $measureNames::TEXT[],
              $controlColumns::TEXT[][],
              '{}'::JSONB[],
              ${values.author}
            ) #$alias;"""
    }

>>>>>>> origin:server/src/main/scala/za/co/absa/atum/server/api/database/Runs.scala
    override protected def slickConverter: GetResult[Unit] = GetResult { _ => }
  }
}
