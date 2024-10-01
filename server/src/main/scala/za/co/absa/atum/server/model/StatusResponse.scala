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

package za.co.absa.atum.server.model

case class StatusResponse(status: String, message: String)

object StatusResponse {

  import io.circe.generic.semiauto._

  implicit val encoder: io.circe.Encoder[StatusResponse] = deriveEncoder
  implicit val decoder: io.circe.Decoder[StatusResponse] = deriveDecoder

  def up: StatusResponse = {
    StatusResponse(
      status = "UP",
      message = "Atum server is up and running"
    )
  }

}
