package za.co.absa.atum.reader

import za.co.absa.atum.model.dto.{AdditionalDataDTO, AdditionalDataItemDTO, CheckpointV2DTO}
import za.co.absa.atum.model.types.BasicTypes.AtumPartitions

import java.time.ZonedDateTime
import java.util.UUID

class Dispatcher {

  /**
   * This method is used to get the Additional data from the server.
   * Mock method to return AdditionalDataDTO
   * @param partitioning : Partitioning to obtain ID for.
   * @return AdditionalDataDTO.
   */
  def getAdditionalData(partitioning: AtumPartitions): AdditionalDataDTO = {
    AdditionalDataDTO(
      data = Map(
        "key1" -> Some(AdditionalDataItemDTO(Some("value1"), "author1")),
        "key2" -> None
      )
    )
  }

  /**
   * This method is used to get the Checkpoints from the server.
   *
   * @param partitioning   : Partitioning to obtain checkpoints for.
   * @param limit          : Limit of checkpoints to return.
   * @param offset         : Offset of checkpoints to return.
   * @param checkpointName : Name of the checkpoint to return.
   * @return List of CheckpointV2DTO.
   */
  def getCheckpoints(
    partitioning: AtumPartitions,
    limit: Option[Int],
    offset: Option[Long],
    checkpointName: Option[String]): Seq[CheckpointV2DTO] = {
    Seq(
      CheckpointV2DTO(
        id = UUID.randomUUID(),
        name = "checkpoint1",
        author = "author1",
        measuredByAtumAgent = true,
        processStartTime = ZonedDateTime.now(),
        processEndTime = Some(ZonedDateTime.now().plusHours(1)),
        measurements = Set.empty
      ),
      CheckpointV2DTO(
        id = UUID.randomUUID(),
        name = "checkpoint2",
        author = "author2",
        processStartTime = ZonedDateTime.now().minusDays(1),
        processEndTime = None,
        measurements = Set.empty
      )
    )
  }
}
