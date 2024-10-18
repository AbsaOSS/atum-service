package za.co.absa.atum.reader

import za.co.absa.atum.model.dto.AdditionalDataDTO
import za.co.absa.atum.model.types.Checkpoint

class Dispatcher {

  /**
   * This method is used to get the Additional data from the server.
   *
   * @param partitioning : Partitioning to obtain ID for.
   * @return AdditionalDataDTO.
   */
  def getAdditionalData(partitioning: Partitioning): AdditionalDataDTO = ???

  /**
   * override protected[agent] def getAdditionalData(partitioning: PartitioningDTO): AdditionalDataDTO = {
   * val partitioningId = getPartitioningId(partitioning)
   * log.debug(s"Got partitioning ID: '$partitioningId'")
   *
   * val request = commonAtumRequest
   * .get(getAdditionalDataEndpoint(partitioningId))
   *
   * val response = backend.send(request)
   *
   * handleResponseBody(response).as[SingleSuccessResponse[AdditionalDataDTO]].data
   * }
   */

  /**
   * This method is used to get the partitioning ID from the server.
   *
   * @param partitioning : Partitioning to obtain ID for.
   * @return Long ID of the partitioning.
   */
  def getCheckpoints(partitioning: Partitioning): Seq[Checkpoint] = ???
}
