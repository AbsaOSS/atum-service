package za.co.absa.atum.server.api.service

import org.junit.jupiter.api.BeforeEach
import org.mockito.{Mockito, MockitoSugar}
import org.scalatest.{BeforeAndAfter, BeforeAndAfterEach}
import za.co.absa.atum.model.dto._
import za.co.absa.atum.server.api.provider.PostgresAccessProvider
import org.scalatest.concurrent.ScalaFutures

import java.time.OffsetDateTime
import java.util.UUID
import za.co.absa.atum.model.dto.MeasureResultDTO.{ResultValueType, TypedValue}
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Future

class DatabaseServiceTest extends AsyncFlatSpec with Matchers with MockitoSugar with ScalaFutures with BeforeAndAfterEach {

    // Mock dependencies
    val postgresAccessProvider: PostgresAccessProvider = mock[PostgresAccessProvider]
    val databaseService = new DatabaseService(postgresAccessProvider)


    "saveCheckpoint" should "return a CompletableFuture containing the saved CheckpointDTO" in {
      // Construct an instance of CheckpointDTOC
      val id = UUID.randomUUID()
      val measure = MeasureDTO("measurementName", Seq("controlColumn1", "controlColumn2"))
      val mainValue = TypedValue("Value1", ResultValueType.String)
      val supportValue = Map("supportKey" -> TypedValue("supportValue", ResultValueType.String))
      val measureResult = MeasureResultDTO(mainValue, supportValue)
      val partition = PartitionDTO("key", "value")
      val partitioning = PartitioningDTO(Seq(partition), Some(Seq(partition)))

      val measurement = MeasurementDTO(measure, measureResult)

      val checkpoint = CheckpointDTO(
        id = id,
        name = "myCheckpoint",
        author = "Author1",
        measuredByAtumAgent = true,
        partitioning = Seq(partition),
        processStartTime = OffsetDateTime.now(),
        processEndTime = Some(OffsetDateTime.now().plusHours(1)),
        measurements = Seq(measurement)
      )

      // Mock behavior of postgresAccessProvider.runs.writeCheckpoint
      when(postgresAccessProvider.runs.writeCheckpoint(checkpoint))
        .thenReturn(Future.successful(Unit))

      // Act & Assert
      val result = databaseService.saveCheckpoint(checkpoint)
      print(result)
    assert(result.get() == checkpoint)
    }

    // Test case 2: SaveCheckpoint Function with Null Input
    "SaveCheckpoint" should "handle null or invalid input gracefully" in {

      val invalidCheckpoint = null

      // Act & Assert
      assertThrows[Exception] {
        databaseService.saveCheckpoint(invalidCheckpoint)
      }
    }

    //  test("testReadCheckpoint") {}

}
