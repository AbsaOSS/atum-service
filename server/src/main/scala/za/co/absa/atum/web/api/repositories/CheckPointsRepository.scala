package za.co.absa.atum.web.api.repositories

import za.co.absa.atum.web.model.Checkpoint

abstract class CheckPointsRepository {
  def save(checkPoint: Checkpoint): Checkpoint
}
