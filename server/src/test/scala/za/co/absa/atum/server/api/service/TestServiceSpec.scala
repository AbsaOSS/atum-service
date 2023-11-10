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

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestContextManager


@SpringBootTest
class TestServiceSpec extends AnyFlatSpec with Matchers {

  @Autowired
  private var testService: TestService = _

  // Makes the above autowired work
  new TestContextManager(this.getClass).prepareTestInstance(this)

  "TestService" should "say greetings" in {
    testService.getMessage shouldEqual "The service says: alfa 'ALFA'"
  }
}
