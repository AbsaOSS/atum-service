/*
 * Copyright 2021 ABSA Group Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package za.co.absa.atum.web.api.controller

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation._
import za.co.absa.atum.web.api.service.ControlMeasureService
import za.co.absa.atum.web.api.implicits._
import za.co.absa.atum.web.model.ControlMeasure

import java.util.{Optional, UUID}
import java.util.concurrent.CompletableFuture

@RestController
@RequestMapping(Array("/api/controlmeasures"))
class ControlMeasureController @Autowired()(controlMeasureService: ControlMeasureService)
  extends BaseApiController(controlMeasureService) {

  @GetMapping(Array("/{flowid}/{segid}"))
  @ResponseStatus(HttpStatus.OK)
  def getListByFlowAndSegIds(@PathVariable flowId: UUID, @PathVariable segId: UUID,
                             @RequestParam limit: Optional[String], @RequestParam offset: Optional[String]
                            ): CompletableFuture[Seq[ControlMeasure]] = {

    val actualLimit = limit.toScalaOption.map(_.toInt).getOrElse(BaseApiController.DefaultLimit)
    val actualOffset = offset.toScalaOption.map(_.toInt).getOrElse(BaseApiController.DefaultOffset)
    controlMeasureService.getListByFlowAndSegIds(flowId, segId, actualLimit, actualOffset)

  }
}
