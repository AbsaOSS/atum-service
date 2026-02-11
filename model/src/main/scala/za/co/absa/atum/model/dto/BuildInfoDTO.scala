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

package za.co.absa.atum.model.dto

import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}

/**
 * BuildInfoDTO represents build metadata for the Atum service.
 *
 * Example:
 * {
 *   "version": "0.6.2",
 *   "fullVersion": "0.6.2-2-g2c6528c"
 * }
 *
 * @param version      The short version string of the build (e.g., "0.6.2").
 * @param fullVersion  The full version string of the build (e.g., "0.6.2-2-g2c6528c").
 */
case class BuildInfoDTO(
  version: String,
  fullVersion: String
)

object BuildInfoDTO {
  implicit val encodeBuildInfoDTO: Encoder[BuildInfoDTO] = deriveEncoder
  implicit val decodeBuildInfoDTO: Decoder[BuildInfoDTO] = deriveDecoder
}
