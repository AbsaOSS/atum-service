package za.co.absa.atum.server.api.service

import org.scalatest.funsuite.AnyFunSuite
import org.mockito.MockitoSugar
import za.co.absa.atum.model.dto.{CheckpointDTO, PartitionDTO}
import za.co.absa.atum.server.api.provider.PostgresAccessProvider

import java.util.UUID
import scala.concurrent.Future

class DatabaseServiceTest extends AnyFunSuite with MockitoSugar {

  test("testSaveCheckpoint should save a checkpoint in the database")  {
    val mockPostgresAccessProvider = mock[PostgresAccessProvider]
    val checkpointDTO = mock[CheckpointDTO]
    val databaseService = new DatabaseService()

    (when(mockPostgresAccessProvider.runs.writeCheckpoint(checkpointDTO))).thenReturn(Future.successful(checkpointDTO))

    val results = databaseService.saveCheckpoint(checkpointDTO)
    println(results)
    results.complete(checkpointDTO)

  }

  test("testReadCheckpoint") {}

  test("testPostgresAccessProvider") {}

}
