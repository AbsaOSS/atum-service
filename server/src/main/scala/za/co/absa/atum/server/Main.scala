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

package za.co.absa.atum.server

import za.co.absa.atum.server.api.controller._
import za.co.absa.atum.server.api.database.{PostgresDatabaseProvider, TransactorProvider}
import za.co.absa.atum.server.api.database.runs.functions.{CreatePartitioningIfNotExists, WriteCheckpoint}
import za.co.absa.atum.server.api.service.{CheckpointServiceImpl, PartitioningServiceImpl}
import za.co.absa.atum.server.repository.{CheckpointRepositoryImpl, PartitioningRepositoryImpl}
import zio.config.typesafe.TypesafeConfigProvider
import zio.logging.consoleLogger
import zio._

object Main extends ZIOAppDefault with Server {

  private val configProvider: ConfigProvider = TypesafeConfigProvider.fromResourcePath()

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
    server
      .provide(
        PartitioningControllerImpl.layer,
        CheckpointControllerImpl.layer,
        PartitioningServiceImpl.layer,
        CheckpointServiceImpl.layer,
        PartitioningRepositoryImpl.layer,
        CheckpointRepositoryImpl.layer,
        CreatePartitioningIfNotExists.layer,
        WriteCheckpoint.layer,
        PostgresDatabaseProvider.layer,
        TransactorProvider.layer,
        zio.Scope.default
      )

  override val bootstrap: ZLayer[Any, Config.Error, Unit] =
    Runtime.removeDefaultLoggers >>> Runtime.setConfigProvider(configProvider) >>> consoleLogger()

}
