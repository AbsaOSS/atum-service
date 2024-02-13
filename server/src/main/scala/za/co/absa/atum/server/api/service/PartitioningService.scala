package za.co.absa.atum.server.api.service

import za.co.absa.atum.model.dto.{AdditionalDataSubmitDTO, PartitioningSubmitDTO}
import za.co.absa.atum.server.api.exception.ServiceError
import za.co.absa.fadb.exceptions.StatusException
import zio.IO
import zio.macros.accessible

@accessible
trait PartitioningService {
  def createPartitioningIfNotExists(partitioning: PartitioningSubmitDTO):
    IO[ServiceError, Either[StatusException, Unit]]

  def createOrUpdateAdditionalData(additionalData: AdditionalDataSubmitDTO):
    IO[ServiceError, Either[StatusException, Unit]]
}
