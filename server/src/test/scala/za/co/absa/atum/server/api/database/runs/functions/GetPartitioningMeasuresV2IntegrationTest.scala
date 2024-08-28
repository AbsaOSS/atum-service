package za.co.absa.atum.server.api.database.runs.functions

import za.co.absa.atum.server.ConfigProviderTest
import za.co.absa.atum.server.api.TestTransactorProvider
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.db.fadb.exceptions.DataNotFoundException
import zio.interop.catz.asyncInstance
import za.co.absa.db.fadb.status.FunctionStatus
import zio.test.{Spec, TestEnvironment, assertTrue}
import zio.{Scope, ZIO}

object GetPartitioningMeasuresV2IntegrationTest extends ConfigProviderTest {

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    suite("GetPartitioningMeasuresSuite")(
      test("Returns expected sequence of Measures with existing partitioning") {
        val partitioningID: Long = 1L

        for {
          getPartitioningMeasuresV2 <- ZIO.service[GetPartitioningMeasuresV2]
          result <- getPartitioningMeasuresV2(partitioningID)
        } yield assertTrue(result == Left(DataNotFoundException(FunctionStatus(41, "No measures found"))))
      }
    ).provide(
      GetPartitioningMeasuresV2.layer,
      PostgresDatabaseProvider.layer,
      TestTransactorProvider.layerWithRollback
    )
  }

}
