package za.co.absa.atum.server.api.database.runs.functions

import za.co.absa.atum.model.dto.PartitionDTO
import za.co.absa.atum.server.ConfigProviderTest
import za.co.absa.atum.server.api.TestTransactorProvider
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.model.PartitioningForDB
import za.co.absa.db.fadb.exceptions.DataNotFoundException
import za.co.absa.db.fadb.status.FunctionStatus
import zio.{Scope, ZIO}
import zio.test.{Spec, TestEnvironment, assertTrue}
import zio.interop.catz.asyncInstance

object GetPartitioningIntegrationTests extends ConfigProviderTest {

  override def spec: Spec[Unit with TestEnvironment with Scope, Any] = {
    suite("GetPartitioningIntegrationTests")(
      test("GetPartitioning returns DataNotFoundException when partitioning not found") {
        for {
          getPartitioningFn <- ZIO.service[GetPartitioning]
          result <- getPartitioningFn(PartitioningForDB.fromSeqPartitionDTO(Seq.empty[PartitionDTO]))
        } yield assertTrue(result == Left(DataNotFoundException(FunctionStatus(41, "Partitioning not found"))))
      }
    )
  }.provide(
    GetPartitioning.layer,
    PostgresDatabaseProvider.layer,
    TestTransactorProvider.layerWithRollback
  )
}
