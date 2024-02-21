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

package za.co.absa.atum.server.api.http

import org.scalatest.funspec.AnyFunSpec
import sttp.client3._
import sttp.client3.playJson._
import sttp.client3.testing.SttpBackendStub
import sttp.tapir.server.stub.TapirStubInterpreter
import sttp.tapir.ztapir.{RIOMonadError, RichZEndpoint}
import za.co.absa.atum.model.dto.CheckpointDTO
import za.co.absa.atum.server.api.TestData
import za.co.absa.atum.server.api.controller.CheckpointController
import za.co.absa.atum.server.model.PlayJsonImplicits.{readsCheckpointDTO, writesCheckpointDTO}

class EndpointsSpec extends AnyFunSpec with Endpoints with TestData { // with Matchers

  describe("CreateCheckpointEndpoint") {

    describe("When success response configured") {
      it("Should produce Right with CheckpointDTO") {
        val createCheckpointBackendStub =
          TapirStubInterpreter(SttpBackendStub.synchronous)
            .whenEndpoint(createCheckpointEndpoint)
            .thenRespond(checkpointDTO1)
            .backend()

        val response = basicRequest
          .post(uri"https://test.com/api/v1/createCheckpoint")
          .body(checkpointDTO1)
          .response(asJson[CheckpointDTO])
          .send(createCheckpointBackendStub)

        response.body match {
          case Left(responseException) => fail(responseException.getMessage)
          case Right(checkpointDTO) => assert(checkpointDTO == checkpointDTO1)
        }
      }
    }

/*        describe("When success response configured"){
          it("Should produce Right with CheckpointDTO"){
            val createCheckpointBackendStub =
    //          TapirStubInterpreter(SttpBackendStub.synchronous)
              TapirStubInterpreter(SttpBackendStub.apply(new RIOMonadError[Any]))
                .whenServerEndpoint(
                  createCheckpointEndpoint
                    .zServerLogic(CheckpointController.createCheckpoint)
                    .widen[CheckpointController]
                )
                .thenRunLogic()
                .backend()

            val response = basicRequest
              .post(uri"https://test.com/api/v1/createCheckpoint")
              .body(checkpointDTO1)
              .response(asJson[CheckpointDTO])
              .send(createCheckpointBackendStub)
    //
    //        response.body match {
    //          case Left(responseException) => fail(responseException.getMessage)
    //          case Right(checkpointDTO) => assert(checkpointDTO == checkpointDTO1)
    //        }
          }
        }
      }*/

  }

}
