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

import org.junit.runner.RunWith
import za.co.absa.atum.model.dto.{AdditionalDataSubmitDTO, PartitionDTO}
import za.co.absa.atum.server.ConfigProviderSpec
import za.co.absa.atum.server.api.TestTransactorProvider
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.fadb.exceptions.DataNotFoundException
import za.co.absa.fadb.status.FunctionStatus
import zio._
import zio.test._
import zio.test.junit.ZTestJUnitRunner

@RunWith(classOf[ZTestJUnitRunner])
class CreateOrUpdateAdditionalDataSpec extends ConfigProviderSpec {

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    suite("CreateOrUpdateAdditionalDataSuite")(
      test("Returns expected Right with Unit") {
        val additionalDataSubmitDTO = AdditionalDataSubmitDTO(
          partitioning = Seq(PartitionDTO("key1", "val1"), PartitionDTO("key2", "val2")),
          additionalData =  Map[String, Option[String]](
            "ownership" -> Some("total"),
            "role" -> Some("primary")
          ),
          author = "testAuthor"
        )
        for {
          createOrUpdateAdditionalData <- ZIO.service[CreateOrUpdateAdditionalData]
          result <- createOrUpdateAdditionalData(additionalDataSubmitDTO)
        } yield assertTrue(result == Left(DataNotFoundException(FunctionStatus(41, "Partitioning not found"))))
      }
    ).provide(
      CreateOrUpdateAdditionalData.layer,
      PostgresDatabaseProvider.layer,
      TestTransactorProvider.layerWithRollback
    )

  }

}
