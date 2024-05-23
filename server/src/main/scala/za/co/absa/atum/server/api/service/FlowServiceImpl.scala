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

package za.co.absa.atum.server.api.service

import za.co.absa.atum.model.dto.{CheckpointDTO, CheckpointQueryDTO, MeasurementDTO, MeasureDTO, MeasureResultDTO}
import za.co.absa.atum.server.api.exception.ServiceError
import za.co.absa.atum.server.api.repository.FlowRepository
import zio._

class FlowServiceImpl(flowRepository: FlowRepository)
  extends FlowService with BaseService {

  override def getFlowCheckpoints(checkpointQueryDTO: CheckpointQueryDTO): IO[ServiceError, Seq[CheckpointDTO]] = {
    repositoryCall(
      flowRepository.getFlowCheckpoints(checkpointQueryDTO), "getFlowCheckpoints"
    ).map({
      checkpointMeasurementsSeq =>
        checkpointMeasurementsSeq.map { cm =>
          CheckpointDTO(
            id = cm.idCheckpoint,
            name = cm.checkpointName,
            author = cm.author,
            measuredByAtumAgent = cm.measuredByAtumAgent,
            partitioning = checkpointQueryDTO.partitioning,
            processStartTime = cm.checkpointStartTime,
            processEndTime = cm.checkpointEndTime,
            measurements = Set(
              MeasurementDTO(
                measure = MeasureDTO(
                  measureName = cm.measureName,
                  measuredColumns = cm.measuredColumns
                ),
                result = MeasureResultDTO(
                  mainValue = MeasureResultDTO.TypedValue(
                    value = cm.measurementValue.hcursor.downField("value").as[String].getOrElse(""),
                    valueType = cm.measurementValue.hcursor.downField("valueType").as[String].getOrElse("") match {
                      case "String"     => MeasureResultDTO.ResultValueType.String
                      case "Long"       => MeasureResultDTO.ResultValueType.Long
                      case "BigDecimal" => MeasureResultDTO.ResultValueType.BigDecimal
                      case "Double"     => MeasureResultDTO.ResultValueType.Double
                      case _            => MeasureResultDTO.ResultValueType.String
                    }
                  ),
                  supportValues = Map.empty
                )
              )
            )
          )
        }
    })
  }

}

object FlowServiceImpl {
  val layer: URLayer[FlowRepository, FlowService] = ZLayer {
    for {
      flowRepository <- ZIO.service[FlowRepository]
    } yield new FlowServiceImpl(flowRepository)
  }
}
