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
