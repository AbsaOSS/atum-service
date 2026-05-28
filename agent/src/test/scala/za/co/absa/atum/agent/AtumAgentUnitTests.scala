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
import za.co.absa.atum.agent.dispatcher.{CapturingDispatcher, ConsoleDispatcher, HttpDispatcher}
import za.co.absa.atum.model.dto.PartitioningSubmitDTO
import za.co.absa.atum.model.types.basic.AtumPartitions

class AtumAgentUnitTests extends AnyFunSuiteLike {

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

  test("AtumAgent can create a config-backed agent instance") {
    val agent = AtumAgent.fromConfig(configOf(Map(
      "atum.dispatcher.type" -> "http",
      "atum.dispatcher.http.url" -> "http://localhost:8080"
    )))

    agent.dispatcher match {
      case _: HttpDispatcher  => succeed
      case _                  => fail("Expected HttpDispatcher")
    }
  }

  test("config-backed agents are independent and use their own currentUser resolution") {
    val partitioning1 = AtumPartitions("domain" -> "one")
    val partitioning2 = AtumPartitions("domain" -> "two")

    final class RecordingAgent(userName: String) extends AtumAgent {
      private var recordedAuthorsInternal: Vector[String] = Vector.empty

      override val dispatcher: CapturingDispatcher =
        AtumAgent.dispatcherFromConfig(configOf(Map(
          "atum.dispatcher.type" -> "capture",
          "atum.dispatcher.capture.capture-limit" -> 10
        ))).asInstanceOf[CapturingDispatcher]

      override private[agent] def currentUser: String = userName

      override def getOrCreateAtumContext(atumPartitions: AtumPartitions): AtumContext = {
        recordedAuthorsInternal = recordedAuthorsInternal :+ this.currentUser
        super.getOrCreateAtumContext(atumPartitions)
      }

      def recordedAuthors: Seq[String] = recordedAuthorsInternal
    }

    val agentA = new RecordingAgent("alice")
    val agentB = new RecordingAgent("bob")

    val contextA = agentA.getOrCreateAtumContext(partitioning1)
    val contextB = agentB.getOrCreateAtumContext(partitioning2)

    assert(contextA.agent == agentA)
    assert(contextB.agent == agentB)
    assert(agentA.recordedAuthors == Seq("alice"))
    assert(agentB.recordedAuthors == Seq("bob"))
    assert(agentA.getOrCreateAtumContext(partitioning1) == contextA)
    assert(agentB.getOrCreateAtumContext(partitioning2) == contextB)
  }

  private def configOf(configValues: Map[String, Any]): Config = {
    val emptyConfig = ConfigFactory.empty()
    configValues.foldLeft(emptyConfig) { case (acc, (configKey, value)) =>
      val configValue = ConfigValueFactory.fromAnyRef(value)
      acc.withValue(configKey, configValue)
    }
  }

}
