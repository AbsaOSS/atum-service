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

import za.co.absa.atum.server.ConfigProviderTest
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.api.{TestData, TestTransactorProvider}
import zio._
import zio.interop.catz.asyncInstance
import zio.test._

object CreatePartitioningIntegrationTests extends ConfigProviderTest with TestData {

  override def spec: Spec[Unit with TestEnvironment with Scope, Any] = {

    suite("CreatePartitioningSuite")(
      test("Returns expected Right") {
        for {
          createPartitioning <- ZIO.service[CreatePartitioning]
          result <- createPartitioning(partitioningSubmitDTO1)
        } yield assertTrue(result.isRight)
      }
    ).provide(
      CreatePartitioning.layer,
      PostgresDatabaseProvider.layer,
      TestTransactorProvider.layerWithRollback
    )

  }

}
