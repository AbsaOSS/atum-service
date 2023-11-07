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

import scala.util.matching.Regex

class Runs (implicit dBEngine: SlickPgEngine) extends DBSchema{
  import Runs._

  val writeCheckpoint = new WriteCheckpoint
}

object Runs {

  private def nestedScalaSeqToPgArray(toConvert: Seq[Seq[String]]): String = {
    val scalaSeqJsonized = toConvert.map(scalaSeqToPgArray).mkString(",")
    s"{$scalaSeqJsonized}"
  }

  private def scalaSeqToPgArray(toConvert: Seq[String]): String = {
    val scalaArrayAndItsContent: Regex = "^\\[(.*)\\]$".r
    val scalaSeqJsonized = SerializationUtils.asJson(toConvert)

    scalaSeqJsonized match {
      case scalaArrayAndItsContent(str) => s"{$str}"
    }
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

      val measureNames = values.measurements.map(_.measure.measureName)
      val measureNamesNormalized = scalaSeqToPgArray(measureNames)

      val controlColumns = values.measurements.map(_.measure.controlColumns)
      val controlColumnsNormalized = nestedScalaSeqToPgArray(controlColumns)

      val measureResults = values.measurements.map(_.result)
      val measureResultsNormalized = measureResults.map(SerializationUtils.asJson)

      sql"""SELECT #$selectEntry
            FROM #$functionName(
              $partitioningNormalized::JSONB,
              ${values.id},
              ${values.name},
              ${values.processStartTime}::TIMESTAMPTZ,
              ${values.processEndTime}::TIMESTAMPTZ,
              $measureNamesNormalized::TEXT[],
              $controlColumnsNormalized::TEXT[][],
              $measureResultsNormalized::JSONB[],
              ${values.author}
            ) #$alias;"""
    }

    override protected def slickConverter: GetResult[Unit] = GetResult { _ => }
  }
}
