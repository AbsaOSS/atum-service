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

import za.co.absa.atum.model.dto._
import za.co.absa.atum.server.api.exception.ServiceError
import za.co.absa.atum.server.api.repository.FlowRepository
import zio._
import io.circe._
import io.circe.parser._
import io.circe.generic.auto._
import io.circe.syntax._
import za.co.absa.atum.server.api.database.DoobieImplicits.encodeTypedValue
import za.co.absa.atum.server.api.database.DoobieImplicits.decodeTypedValue
import za.co.absa.atum.server.api.database.DoobieImplicits.encodeResultValueType
import za.co.absa.atum.server.api.database.DoobieImplicits.decodeResultValueType

class FlowServiceImpl(flowRepository: FlowRepository)
  extends FlowService with BaseService {

  def extractMainValueFromMeasurementValue(json: Json): Either[Error, MeasureResultDTO.TypedValue] = {
    json.as[MeasureResultDTO].map(_.mainValue)
  }

  def extractSupportValuesFromMeasurementValue(json: Json): Either[Error, Map[String, MeasureResultDTO.TypedValue]] = {
    json.as[MeasureResultDTO].map(_.supportValues)
  }

  def parseCheckpointQueryResultDTO(measurementValue: Json): Either[Error, MeasureResultDTO] = {
    for {
      mainValue <- extractMainValueFromMeasurementValue(measurementValue)
      supportValues <- extractSupportValuesFromMeasurementValue(measurementValue)
    } yield MeasureResultDTO(mainValue, supportValues)
  }

  def extractSupportValuesFromMeasurementValue2(json: Json): Either[Error, Map[String, MeasureResultDTO.TypedValue]] = {
    json.as[MeasureResultDTO].map(_.supportValues)
  }

  def parseCheckpointQueryResultDTO2(jsonString: String): Either[Error, Map[String, MeasureResultDTO.TypedValue]] = {
    for {
      parsedJson <- parse(jsonString)
      checkpoint <- parsedJson.as[CheckpointQueryResultDTO]
      supportValues <- extractSupportValuesFromMeasurementValue2(checkpoint.measurementValue)
    } yield supportValues
  }

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
                result = parseCheckpointQueryResultDTO(cm.measurementValue)
                  .getOrElse(  // todo no error silencing!
                    MeasureResultDTO(
                      mainValue = MeasureResultDTO.TypedValue("", MeasureResultDTO.ResultValueType.String),
                      supportValues = Map.empty
                    )
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
