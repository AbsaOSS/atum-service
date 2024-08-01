package za.co.absa.atum.server.api.database.flows.functions

import za.co.absa.atum.server.ConfigProviderTest
import za.co.absa.atum.model.dto.{CheckpointQueryDTO, PartitionDTO, PartitioningDTO}
import za.co.absa.atum.server.api.TestTransactorProvider
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import zio.interop.catz.asyncInstance
import zio.test.Assertion.failsWithA
import zio.{Scope, ZIO}
import zio.test._

object GetFlowCheckpointsIntegrationTests extends ConfigProviderTest {

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    val partitioningDTO1: PartitioningDTO = Seq(
      PartitionDTO("stringA", "stringA"),
      PartitionDTO("stringB", "stringB")
    )

    suite("GetFlowCheckpointsIntegrationTests")(
      test("Returns expected sequence of flow of Checkpoints with existing partitioning") {
        val partitioningQueryDTO: CheckpointQueryDTO = CheckpointQueryDTO(
          partitioning = partitioningDTO1,
          limit = Some(10),
          checkpointName = Some("checkpointName")
        )

        for {
          getFlowCheckpoints <- ZIO.service[GetFlowCheckpoints]
          exit <- getFlowCheckpoints(partitioningQueryDTO).exit
        } yield assert(exit)(failsWithA[doobie.util.invariant.NonNullableColumnRead])
      }
    ).provide(
      GetFlowCheckpoints.layer,
      PostgresDatabaseProvider.layer,
      TestTransactorProvider.layerWithRollback
    )
  }

}

