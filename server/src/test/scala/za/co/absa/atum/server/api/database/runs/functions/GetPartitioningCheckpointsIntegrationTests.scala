package za.co.absa.atum.server.api.database.runs.functions

import doobie.util.Read
import za.co.absa.atum.server.ConfigProviderTest
import za.co.absa.atum.model.dto.{CheckpointQueryDTO, PartitionDTO, PartitioningDTO}
import za.co.absa.atum.server.api.TestTransactorProvider
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.model.CheckpointMeasurements
import zio.test.Assertion.failsWithA
import zio.{Scope, ZIO}
import zio.test._
import doobie.postgres.implicits._
import doobie.postgres.circe.jsonb.implicits.jsonbGet
import za.co.absa.atum.server.api.database.DoobieImplicits.Sequence._

object GetPartitioningCheckpointsIntegrationTests extends ConfigProviderTest {

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    val partitioningDTO1: PartitioningDTO = Seq(
      PartitionDTO("string1", "string1"),
      PartitionDTO("string2", "string2")
    )

    suite("GetPartitioningCheckpointsIntegrationTests")(
      test("Returns expected sequence of Checkpoints with existing partitioning") {
        val partitioningQueryDTO: CheckpointQueryDTO = CheckpointQueryDTO(
          partitioning = partitioningDTO1,
          limit = Some(10),
          checkpointName = Some("checkpointName")
        )
        // Read[CheckpointMeasurements] implicit validation
        Read[CheckpointMeasurements]

        for {
          getPartitioningCheckpoints <- ZIO.service[GetPartitioningCheckpoints]
          exit <- getPartitioningCheckpoints(partitioningQueryDTO).exit
        } yield assert(exit)(failsWithA[doobie.util.invariant.NonNullableColumnRead])
      }
    ).provide(
      GetPartitioningCheckpoints.layer,
      PostgresDatabaseProvider.layer,
      TestTransactorProvider.layerWithRollback
    )
  }

}

