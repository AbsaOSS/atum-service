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

package za.co.absa.atum.agent.exception

/**
 * This type represents a base class for exceptions thrown by the Atum Agent.
 *
 * @param message A message describing the exception.
 */
abstract class AtumAgentException(message: String) extends Exception(message)

/**
 * This object contains possible exceptions thrown by the Atum Agent.
 */
object AtumAgentException {
  /**
   * This type represents an exception related to creation of provided measurement.
   *
   * @param message A message describing the exception.
   */
  case class MeasurementException(message: String) extends AtumAgentException(message)

  /**
   * This type represents an exception related to HTTP communication.
   * @param statusCode A status code of the HTTP response.
   * @param message A message describing the exception.
   */
  case class HttpException(statusCode: Int, message: String) extends AtumAgentException(message)
}
