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

package za.co.absa.atum.agent

import com.typesafe.config.{Config, ConfigException, ConfigFactory, ConfigValueFactory}
import org.scalatest.funsuite.AnyFunSuiteLike
import za.co.absa.atum.agent.AtumContext.AtumPartitions
import za.co.absa.atum.agent.dispatcher.{CapturingDispatcher, ConsoleDispatcher, HttpDispatcher}

class AtumAgentUnitTest extends AnyFunSuiteLike {

  test("AtumAgent creates AtumContext(s) as expected") {
    val atumAgent = AtumAgent
    val atumPartitions = AtumPartitions("abc" -> "def")
    val subPartitions = AtumPartitions("ghi", "jkl")

    val atumContext1 = atumAgent.getOrCreateAtumContext(atumPartitions)
    val atumContext2 = atumAgent.getOrCreateAtumContext(atumPartitions)

    // AtumAgent returns expected instance of AtumContext
    assert(atumAgent.getOrCreateAtumContext(atumPartitions) == atumContext1)
    assert(atumContext1 == atumContext2)

    // AtumSubContext contains expected AtumPartitions
    val atumSubContext = atumAgent.getOrCreateAtumSubContext(subPartitions)(atumContext1)
    assert(atumSubContext.atumPartitions == (atumPartitions ++ subPartitions))

    // AtumContext contains reference to expected AtumAgent
    assert(atumSubContext.agent == atumAgent)
  }

  test("AtumAgent creates dispatcher per configuration") {
    def configForDispatcher(dispatcherType: String): Config = {
      val emptyConfig = ConfigFactory.empty()
      val value = ConfigValueFactory.fromAnyRef(dispatcherType)
      emptyConfig.withValue("atum.dispatcher.type", value)
    }

    def configOf(configValues: Map[String, Any]): Config = {
      val emptyConfig = ConfigFactory.empty()
      configValues.foldLeft(emptyConfig) { case (acc, (configKey, value)) =>
        val configValue = ConfigValueFactory.fromAnyRef(value)
        acc.withValue(configKey, configValue)
      }
    }

    AtumAgent.dispatcherFromConfig(configOf(Map(
      "atum.dispatcher.type" -> "http",
      "atum.dispatcher.http.url" -> "http://localhost:8080"
    ))) match {
      case _: HttpDispatcher  => info("HttpDispatcher created successfully")
      case _                  => fail("Expected HttpDispatcher")
    }

    AtumAgent.dispatcherFromConfig(configOf(Map("atum.dispatcher.type" -> "console"))) match {
      case _: ConsoleDispatcher => info("ConsoleDispatcher created successfully")
      case _                    => fail("Expected ConsoleDispatcher")
    }

    AtumAgent.dispatcherFromConfig(configOf(Map(
      "atum.dispatcher.type" -> "capture",
      "atum.dispatcher.capture.capture-limit" -> 0
    ))) match {
      case _: CapturingDispatcher => info("CapturingDispatcher created successfully")
      case _                      => fail("Expected CapturingDispatcher")
    }

    val eUnknown = intercept[UnsupportedOperationException] {
      AtumAgent.dispatcherFromConfig(configOf(Map("atum.dispatcher.type" -> "unknown")))
    }
    assert(eUnknown.getMessage.contains("Unsupported dispatcher type: 'unknown'"))
    info("unknown dispatcher type throws exception as expected")

    val eNoConfig = intercept[ConfigException.Missing] {
      AtumAgent.dispatcherFromConfig(configOf(Map.empty))
    }
    assert(eNoConfig.getMessage.contains("No configuration setting found for key"))
    info("missing dispatcher configuration throws exception as expected")

  }

}
