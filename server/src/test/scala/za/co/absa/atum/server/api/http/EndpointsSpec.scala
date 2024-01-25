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

//package za.co.absa.atum.server.api.http
//
//import org.scalatest.funspec.AnyFunSpec
//import org.scalatest.matchers.should.Matchers
//import sttp.client3._
//import sttp.client3.testing.SttpBackendStub
//import sttp.tapir.server.stub.TapirStubInterpreter
//import za.co.absa.atum.server.api.TestData
//
//class EndpointsSpec extends AnyFunSpec with Matchers with Endpoints with TestData {
//
//  describe("CreateCheckpointEndpoint"){
//    describe("When success response configured"){
//      it("Should produce Right with CheckpointDTO"){
//        val createCheckpointBackendStub =
//          TapirStubInterpreter(SttpBackendStub.synchronous)
//            .whenEndpoint(createCheckpointEndpoint)
//            .thenRespond(checkpointDTO1)
//            .backend()
//
//        val response = basicRequest
//          .post(uri"http://test.com/api/v1/createCheckpoint")
//          .send(createCheckpointBackendStub)
//
//        println(response.body)
//
//        assert(response.body == 0)
//      }
//    }
//  }
//
//}
