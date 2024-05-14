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

package za.co.absa.atum.server.model

import io.circe.Json
import za.co.absa.atum.model.dto.PartitioningDTO

import java.time.ZonedDateTime
import java.util.UUID
//import doobie.implicits.javatime._
//import doobie.util.{Get, Read}
//import doobie.postgres.circe.json.implicits.jsonGet
//import doobie.postgres.circe.jsonb.implicits.jsonbGet

case class CheckpointMeasurements(
  idCheckpoint: UUID,
  checkpointName: String,
  author: String,
  measuredByAtumAgent: Boolean = false,
  partitioning: PartitioningDTO,
  measureName: String,
  measureColumns: Seq[String],
  measurementValue: Json, // TODO MeasureResultDTO
  checkpointStartTime: ZonedDateTime,
  checkpointEndTime:  Option[ZonedDateTime]
)
