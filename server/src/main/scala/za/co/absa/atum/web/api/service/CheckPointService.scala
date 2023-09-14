package za.co.absa.atum.web.api.service

import za.co.absa.atum.web.api.repositories.CheckPointsRepository
import za.co.absa.atum.web.model.Checkpoint

class CheckPointService (private val repository: CheckPointsRepository) {
  def addCheckPoint(checkPoint: Checkpoint): Checkpoint = {
    repository.save(checkPoint)
  }
}
