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

import org.scalatest.flatspec.AnyFlatSpec

class BaseEndpointsUnitTests extends AnyFlatSpec {

  object BaseEndpointsForTests extends BaseEndpoints

  "pathToAPIv1CompatibleFormat" should "successfully handle empty input" in {
    val input = ""
    val actual = BaseEndpointsForTests.pathToAPIv1CompatibleFormat(input)
    val expected = ""
    assert(actual == expected)
  }

  "pathToAPIv1CompatibleFormat" should
    "successfully convert our standard API path format to format compatible with API V1 (kebab)" in {

    val input = "create-checkpoint"
    val actual = BaseEndpointsForTests.pathToAPIv1CompatibleFormat(input)
    val expected = "createCheckpoint"
    assert(actual == expected)
  }

  "pathToAPIv1CompatibleFormat" should
    "successfully convert our standard API path format to format compatible with API V1 (kebab2)" in {

    val input = "create-check-point2"
    val actual = BaseEndpointsForTests.pathToAPIv1CompatibleFormat(input)
    val expected = "createCheckPoint2"
    assert(actual == expected)
  }

  "pathToAPIv1CompatibleFormat" should
    "successfully convert our standard API path format to format compatible with API V1 (kebab3)" in {

    val input = "Create-check-"
    val actual = BaseEndpointsForTests.pathToAPIv1CompatibleFormat(input)
    val expected = "createCheck"
    assert(actual == expected)
  }

  "pathToAPIv1CompatibleFormat" should
    "successfully convert our standard API path format to format compatible with API V1 (snake)" in {

    val input = "_create_check_point"
    val actual = BaseEndpointsForTests.pathToAPIv1CompatibleFormat(input)
    val expected = "CreateCheckPoint"
    assert(actual == expected)
  }

  "pathToAPIv1CompatibleFormat" should
    "successfully convert our standard API path format to format compatible with API V1 (kebab and snake)" in {

    val input = "Create-check_Point"
    val actual = BaseEndpointsForTests.pathToAPIv1CompatibleFormat(input)
    val expected = "createCheckPoint"
    assert(actual == expected)
  }

  "pathToAPIv1CompatibleFormat" should
    "successfully convert our standard API path format to format compatible with API V1 (one word)" in {

    val input = "createcheckpoint"
    val actual = BaseEndpointsForTests.pathToAPIv1CompatibleFormat(input)
    val expected = "createcheckpoint"
    assert(actual == expected)
  }
}
