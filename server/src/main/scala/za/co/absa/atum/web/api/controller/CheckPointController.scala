package za.co.absa.atum.web.api.controller

import io.swagger.v3.oas.annotations.parameters.RequestBody
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.{HttpStatus, ResponseEntity}
import org.springframework.web.bind.annotation.PostMapping
import za.co.absa.atum.model.Checkpoint
import za.co.absa.atum.web.api.service.CheckpointService

@Autowired()
class CheckPointController(private val checkpointService: CheckpointService = null) {
  @PostMapping("/checkpoint")
  def saveCheckpoint(@RequestBody checkpoint: Checkpoint): ResponseEntity[Unit] = {
    checkpointService.saveCheckpoint(checkpoint)
    ResponseEntity.status(HttpStatus.CREATED).build()
  }
}
