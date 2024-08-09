package za.co.absa.atum.server.api.database.runs.functions

import za.co.absa.atum.model.ResultValueType
import za.co.absa.atum.model.dto._
import za.co.absa.atum.model.dto.MeasureResultDTO.TypedValue
import za.co.absa.atum.server.ConfigProviderTest
import za.co.absa.atum.server.api.TestTransactorProvider
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.model.WriteCheckpointV2Args
import za.co.absa.db.fadb.exceptions.DataConflictException
import za.co.absa.db.fadb.status.{FunctionStatus, Row}
import zio._
import zio.interop.catz.asyncInstance
import zio.test._

import java.time.ZonedDateTime
import java.util.UUID

object WriteCheckpointV2IntegrationTests extends ConfigProviderTest {

  override def spec: Spec[TestEnvironment with Scope, Any] = {

    suite("WriteCheckpointV2Suite")(
      test("Returns expected Left with DataNotFoundException as related partitioning is not in the database") {
        val checkpointV2DTO = CheckpointV2DTO(
          id = UUID.randomUUID(),
          name = "name",
          author = "author",
          processStartTime = ZonedDateTime.now(),
          processEndTime = Option(ZonedDateTime.now()),
          measurements = Set(
            MeasurementDTO(MeasureDTO("count", Seq("*")), MeasureResultDTO(TypedValue("1", ResultValueType.LongValue)))
          )
        )
        for {
          writeCheckpointV2 <- ZIO.service[WriteCheckpointV2]
          result <- writeCheckpointV2(WriteCheckpointV2Args(1L, checkpointV2DTO))
        } yield assertTrue(result == Left(DataConflictException(FunctionStatus(32, "Partitioning not found"))))
      }
    ).provide(
      WriteCheckpointV2.layer,
      PostgresDatabaseProvider.layer,
      TestTransactorProvider.layerWithRollback
    )
  }

}
