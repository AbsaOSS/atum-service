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

package za.co.absa.atum.reader

import cats.Monad
import za.co.absa.atum.model.types.BasicTypes.{AdditionalData, AtumPartitions}
import za.co.absa.atum.model.types.Checkpoint
import za.co.absa.atum.reader.server.GenericServerConnection

import scala.language.higherKinds

class PartitioningReader[F[_]: Monad](partitioning: AtumPartitions)(
  implicit serverConnection: GenericServerConnection[F], dispatcher: Dispatcher) {

  /**
   * Fetches additional data for the given partitioning.
   * @param partitioning The partitioning for which to fetch additional data.
   * @return AdditionalData containing the additional data.
   */
  def getAdditionalData: F[AdditionalData] = {
    Monad[F].pure(dispatcher.getAdditionalData(partitioning).data.map {
      case (key, Some(itemDTO)) => key -> Some(itemDTO.value)
      case (key, None) => key -> None
    })
  }

  /**
   * Fetches checkpoints for the given partitioning.
   * @param partitioning The partitioning for which to fetch checkpoints.
   * @return List of CheckpointDTO containing the checkpoints.
   */
  def getCheckpoints(limit: Option[Int], offset: Option[Long], checkpointName: Option[String]): F[List[Checkpoint]] = {
    Monad[F].pure(dispatcher.getCheckpoints(partitioning, limit, offset, checkpointName).map { dto =>
      Checkpoint(
        id = dto.id.toString,
        name = dto.name,
        author = dto.author,
        measuredByAtumAgent = dto.measuredByAtumAgent,
        processStartTime = dto.processStartTime,
        processEndTime = dto.processEndTime,
        measurements = dto.measurements
      )
    }.toList)
  }

}

object PartitioningReader {
  def apply[F[_]: Monad](partitioning: AtumPartitions)(
    implicit serverConnection: GenericServerConnection[F], dispatcher: Dispatcher): PartitioningReader[F] =
    new PartitioningReader[F](partitioning)
}
