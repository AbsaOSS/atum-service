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

import org.springframework.jdbc.core.JdbcTemplate
import za.co.absa.atum.model.dto.CheckpointDTO

import slick.jdbc.{GetResult, PositionedResult, SQLActionBuilder}
import za.co.absa.fadb.DBFunction._
import za.co.absa.fadb.DBSchema
import za.co.absa.fadb.slick.{SlickFunction, SlickFunctionWithStatusSupport, SlickPgEngine}
import za.co.absa.fadb.status.handling.implementations.StandardStatusHandling
import za.co.absa.fadb.naming.implementations.SnakeCaseNaming.Implicits._


class CheckpointRepository extends
  DBSingleResultFunction[CheckpointDTO, Unit, SlickPgEngine]
  with SlickFunctionWithStatusSupport[CheckpointDTO, Unit]
  with StandardStatusHandling {

  private val jdbcTemplate: JdbcTemplate = null
  def createCheckout(checkpoint: CheckpointDTO): Unit = {
    jdbcTemplate.execute(
      "SELECT runs.open_checkpoint(?, ?, ?, ?, ?, ?)",
      checkpoint.id,
      checkpoint.name,
      checkpoint.author,
      checkpoint.partitioning,
      checkpoint.processStartTime,
      checkpoint.processEndTime,
      checkpoint.measurements
    )
  }
}
