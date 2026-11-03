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
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{mock, never, times, verify, when}
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.mockito.ArgumentCaptor
import org.scalatest.funsuite.AnyFunSuiteLike
import za.co.absa.atum.agent.dispatcher.{CapturingDispatcher, ConsoleDispatcher, Dispatcher, HttpDispatcher}
import za.co.absa.atum.agent.exception.AtumAgentException.PartitioningUpdateException
import za.co.absa.atum.agent.model.PartitioningCreateRequest
import za.co.absa.atum.model.dto.AtumContextDTO
import za.co.absa.atum.model.types.basic.{AtumPartitions, AtumPartitionsOps}

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

    // AtumSubContext contains expected AtumPartitions (merge mode)
    val atumSubContext = atumAgent.getOrCreateAtumSubContext(subPartitions)(atumContext1)
    assert(atumSubContext.atumPartitions == (atumPartitions ++ subPartitions))

    // AtumContext contains reference to expected AtumAgent
    assert(atumSubContext.agent == atumAgent)
  }

  test("getOrCreateAtumSubContext with mergeWithParent=false uses only sub partitions") {
    val atumAgent = AtumAgent
    val atumPartitions = AtumPartitions("parent" -> "value")
    val subPartitions = AtumPartitions("child" -> "value")

    val parentContext = atumAgent.getOrCreateAtumContext(atumPartitions)
    val childContext = atumAgent.getOrCreateAtumSubContext(subPartitions, mergeWithParent = false)(parentContext)

    // Child partitioning should contain ONLY the sub partitions, not parent's keys
    assert(childContext.atumPartitions == subPartitions)
    assert(!childContext.atumPartitions.contains("parent"))
    assert(childContext.agent == atumAgent)
  }

  test("subPartitionContext with mergeWithParent=false skips overlap check") {
    val atumAgent = AtumAgent
    val atumPartitions = AtumPartitions("key" -> "parentVal")

    val parentContext = atumAgent.getOrCreateAtumContext(atumPartitions)

    // With merge=true, overlapping keys would throw
    intercept[PartitioningUpdateException] {
      parentContext.subPartitionContext(AtumPartitions("key" -> "childVal"), mergeWithParent = true)
    }

    // With merge=false, overlapping keys are allowed (child stands alone)
    val childContext = parentContext.subPartitionContext(AtumPartitions("key" -> "childVal"), mergeWithParent = false)
    assert(childContext.atumPartitions == AtumPartitions("key" -> "childVal"))
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
    val sharedPartitioning = AtumPartitions("domain" -> "one")

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

    val contextA1 = agentA.getOrCreateAtumContext(sharedPartitioning)
    val contextA2 = agentA.getOrCreateAtumContext(sharedPartitioning)
    val contextB1 = agentB.getOrCreateAtumContext(sharedPartitioning)
    val contextB2 = agentB.getOrCreateAtumContext(sharedPartitioning)

    assert(contextA1 eq contextA2)
    assert(contextB1 eq contextB2)
    assert(contextA1 ne contextB1)

    assert(contextA1.agent == agentA)
    assert(contextB1.agent == agentB)

    assert(agentA.recordedAuthors == Seq("alice", "alice"))
    assert(agentB.recordedAuthors == Seq("bob", "bob"))
  }

  test("AtumAgent.fromConfig creates independent agent instances with separate context stores") {
    val sharedPartitioning = AtumPartitions("domain" -> "one")

    val configA = configOf(Map(
      "atum.dispatcher.type" -> "capture",
      "atum.dispatcher.capture.capture-limit" -> 10
    ))
    val configB = configOf(Map(
      "atum.dispatcher.type" -> "capture",
      "atum.dispatcher.capture.capture-limit" -> 10
    ))

    val agentA = AtumAgent.fromConfig(configA)
    val agentB = AtumAgent.fromConfig(configB)

    val contextA1 = agentA.getOrCreateAtumContext(sharedPartitioning)
    val contextA2 = agentA.getOrCreateAtumContext(sharedPartitioning)
    val contextB1 = agentB.getOrCreateAtumContext(sharedPartitioning)
    val contextB2 = agentB.getOrCreateAtumContext(sharedPartitioning)

    assert(contextA1 eq contextA2)
    assert(contextB1 eq contextB2)
    assert(contextA1 ne contextB1)

    assert(contextA1.agent == agentA)
    assert(contextB1.agent == agentB)
    assert(agentA.dispatcher ne agentB.dispatcher)
  }

  test("currentUser falls back to the JVM user when atum.author is not set") {
    val agent = AtumAgent.fromConfig(configOf(Map(
      "atum.dispatcher.type" -> "capture",
      "atum.dispatcher.capture.capture-limit" -> 10
    )))

    assert(agent.currentUser == System.getProperty("user.name"))
  }

  test("currentUser uses atum.author from config when set (trimmed)") {
    val agent = AtumAgent.fromConfig(configOf(Map(
      "atum.dispatcher.type" -> "capture",
      "atum.dispatcher.capture.capture-limit" -> 10,
      "atum.author" -> "  my-application  "
    )))

    assert(agent.currentUser == "my-application")
  }

  test("currentUser falls back to the JVM user when atum.author is blank") {
    val agent = AtumAgent.fromConfig(configOf(Map(
      "atum.dispatcher.type" -> "capture",
      "atum.dispatcher.capture.capture-limit" -> 10,
      "atum.author" -> "   "
    )))

    assert(agent.currentUser == System.getProperty("user.name"))
  }

  test("currentUser is resolved independently per config-backed agent") {
    val agentA = AtumAgent.fromConfig(configOf(Map(
      "atum.dispatcher.type" -> "capture",
      "atum.dispatcher.capture.capture-limit" -> 10,
      "atum.author" -> "alice"
    )))
    val agentB = AtumAgent.fromConfig(configOf(Map(
      "atum.dispatcher.type" -> "capture",
      "atum.dispatcher.capture.capture-limit" -> 10
    )))

    assert(agentA.currentUser == "alice")
    assert(agentB.currentUser == System.getProperty("user.name"))
  }

  test("currentUser is resolved once and cached for agents that do not override it") {
    val originalAuthor = Option(System.getProperty("atum.author"))
    try {
      System.setProperty("atum.author", "first-app")
      ConfigFactory.invalidateCaches()

      // a custom agent that does NOT override currentUser -> relies on the cached trait default
      val agent = new AtumAgent {
        override val dispatcher: CapturingDispatcher =
          AtumAgent.dispatcherFromConfig(configOf(Map(
            "atum.dispatcher.type" -> "capture",
            "atum.dispatcher.capture.capture-limit" -> 10
          ))).asInstanceOf[CapturingDispatcher]
      }

      // first access resolves and caches the identity
      assert(agent.currentUser == "first-app")

      // change the backing system property and invalidate Typesafe caches:
      // a recomputing `def` would observe the new value here
      System.setProperty("atum.author", "second-app")
      ConfigFactory.invalidateCaches()

      // the resolved-once value must remain stable for the agent's lifetime
      assert(agent.currentUser == "first-app")
    } finally {
      originalAuthor match {
        case Some(value) => System.setProperty("atum.author", value)
        case None        => System.clearProperty("atum.author")
      }
      ConfigFactory.invalidateCaches()
    }
  }

  private class MockedDispatcherAgent extends AtumAgent {
    override val dispatcher: Dispatcher = mock(classOf[Dispatcher])
    override private[agent] def currentUser: String = "multi-parent-user"

    def parent(partitions: AtumPartitions): AtumContext =
      AtumContext.fromDTO(AtumContextDTO(partitions.toPartitioningDTO), this)

    def stubDispatcher(): Unit =
      when(dispatcher.createPartitioning(any[PartitioningCreateRequest])).thenAnswer(new Answer[AtumContextDTO] {
        override def answer(invocation: InvocationOnMock): AtumContextDTO =
          AtumContextDTO(invocation.getArgument[PartitioningCreateRequest](0).partitioning)
      })

    def captured(): PartitioningCreateRequest = {
      val captor = ArgumentCaptor.forClass(classOf[PartitioningCreateRequest])
      verify(dispatcher, times(1)).createPartitioning(captor.capture())
      captor.getValue
    }
  }

  private val mp1 = AtumPartitions("p" -> "1")
  private val mp2 = AtumPartitions("p" -> "2")
  private val mp3 = AtumPartitions("p" -> "3")
  private val mpChild = AtumPartitions("child" -> "c")

  test("multi-parent getOrCreateAtumSubContext dispatches once with all parents and child identity only") {
    val agent = new MockedDispatcherAgent
    agent.stubDispatcher()
    val parents = Seq(mp1, mp2, mp3).map(agent.parent)

    val child = agent.getOrCreateAtumSubContext(mpChild, parents)

    val dto = agent.captured()
    assert(dto.parentPartitionings == Seq(mp1, mp2, mp3).map(_.toPartitioningDTO))
    assert(dto.authorIfNew == "multi-parent-user")
    assert(dto.partitioning == mpChild.toPartitioningDTO)
    assert(child.atumPartitions == mpChild)
    assert(child.agent == agent)
  }

  test("multi-parent getOrCreateAtumSubContext de-duplicates parents by partitions") {
    val agent = new MockedDispatcherAgent
    agent.stubDispatcher()
    val parents = Seq(agent.parent(mp1), agent.parent(mp1), agent.parent(mp2))

    agent.getOrCreateAtumSubContext(mpChild, parents)

    assert(agent.captured().parentPartitionings == Seq(mp1, mp2).map(_.toPartitioningDTO))
  }

  test("multi-parent getOrCreateAtumSubContext rejects empty parents without dispatching") {
    val agent = new MockedDispatcherAgent

    intercept[IllegalArgumentException] {
      agent.getOrCreateAtumSubContext(mpChild, Seq.empty)
    }
    verify(agent.dispatcher, never()).createPartitioning(any[PartitioningCreateRequest])
  }

  test("multi-parent getOrCreateAtumSubContext accepts a single-element Seq") {
    val agent = new MockedDispatcherAgent
    agent.stubDispatcher()

    agent.getOrCreateAtumSubContext(mpChild, Seq(agent.parent(mp1)))

    assert(agent.captured().parentPartitionings == Seq(mp1.toPartitioningDTO))
  }

  test("getOrCreateAtumContext dispatches a PartitioningCreateRequest with no parents and the current user") {
    val agent = new MockedDispatcherAgent
    agent.stubDispatcher()

    agent.getOrCreateAtumContext(mpChild)

    assert(agent.captured().parentPartitionings.isEmpty)
    assert(agent.captured().authorIfNew == "multi-parent-user")
  }

  test("single-parent getOrCreateAtumSubContext dispatches a PartitioningCreateRequest with exactly that parent") {
    val agent = new MockedDispatcherAgent
    agent.stubDispatcher()
    val subPartitions = AtumPartitions("s" -> "1")

    agent.getOrCreateAtumSubContext(subPartitions, mergeWithParent = false)(agent.parent(mp1))

    assert(agent.captured().parentPartitionings == Seq(mp1.toPartitioningDTO))
    assert(agent.captured().partitioning == subPartitions.toPartitioningDTO)
  }

  // ---- copyMeasureDefinitions flag: pre-existing overloads send false, new overloads send what they are given ----

  private val flagSub = AtumPartitions("s" -> "1")

  private val flagRows: Seq[(String, MockedDispatcherAgent => Unit, Boolean)] = Seq(
    ("getOrCreateAtumContext (root)", a => a.getOrCreateAtumContext(mpChild), false),
    ("1-arg getOrCreateAtumSubContext", a => a.getOrCreateAtumSubContext(flagSub)(a.parent(mp1)), false),
    (
      "2-arg getOrCreateAtumSubContext mergeWithParent = true",
      a => a.getOrCreateAtumSubContext(flagSub, mergeWithParent = true)(a.parent(mp1)),
      false
    ),
    (
      "2-arg getOrCreateAtumSubContext mergeWithParent = false",
      a => a.getOrCreateAtumSubContext(flagSub, mergeWithParent = false)(a.parent(mp1)),
      false
    ),
    (
      "multi-parent 2-arg getOrCreateAtumSubContext (2 parents)",
      a => a.getOrCreateAtumSubContext(mpChild, Seq(a.parent(mp1), a.parent(mp2))),
      false
    ),
    (
      "multi-parent 2-arg getOrCreateAtumSubContext (1 parent)",
      a => a.getOrCreateAtumSubContext(mpChild, Seq(a.parent(mp1))),
      false
    ),
    (
      "3-arg getOrCreateAtumSubContext merge = true, copy = false",
      a => a.getOrCreateAtumSubContext(flagSub, mergeWithParent = true, copyMeasureDefinitions = false)(a.parent(mp1)),
      false
    ),
    (
      "3-arg getOrCreateAtumSubContext merge = false, copy = false",
      a => a.getOrCreateAtumSubContext(flagSub, mergeWithParent = false, copyMeasureDefinitions = false)(a.parent(mp1)),
      false
    ),
    (
      "3-arg getOrCreateAtumSubContext merge = true, copy = true",
      a => a.getOrCreateAtumSubContext(flagSub, mergeWithParent = true, copyMeasureDefinitions = true)(a.parent(mp1)),
      true
    ),
    (
      "3-arg getOrCreateAtumSubContext merge = false, copy = true",
      a => a.getOrCreateAtumSubContext(flagSub, mergeWithParent = false, copyMeasureDefinitions = true)(a.parent(mp1)),
      true
    ),
    (
      "multi-parent 3-arg copy = false",
      a => a.getOrCreateAtumSubContext(mpChild, Seq(a.parent(mp1), a.parent(mp2)), copyMeasureDefinitions = false),
      false
    ),
    (
      "multi-parent 3-arg copy = true (2 parents)",
      a => a.getOrCreateAtumSubContext(mpChild, Seq(a.parent(mp1), a.parent(mp2)), copyMeasureDefinitions = true),
      true
    ),
    (
      "multi-parent 3-arg copy = true (1 parent)",
      a => a.getOrCreateAtumSubContext(mpChild, Seq(a.parent(mp1)), copyMeasureDefinitions = true),
      true
    )
  )

  flagRows.foreach { case (name, call, expectedFlag) =>
    test(s"copyMeasureDefinitions is $expectedFlag with exactly one dispatch for: $name") {
      val agent = new MockedDispatcherAgent
      agent.stubDispatcher()

      call(agent)

      assert(agent.captured().copyMeasureDefinitions == expectedFlag)
    }
  }

  test("3-arg single-parent overload with merge = true and copy = true: child is parent ++ sub, parent recorded") {
    val agent = new MockedDispatcherAgent
    agent.stubDispatcher()

    val child = agent.getOrCreateAtumSubContext(flagSub, mergeWithParent = true, copyMeasureDefinitions = true)(
      agent.parent(mp1)
    )

    val dto = agent.captured()
    assert(dto.copyMeasureDefinitions)
    assert(dto.partitioning == (mp1 ++ flagSub).toPartitioningDTO)
    assert(dto.parentPartitionings == Seq(mp1.toPartitioningDTO))
    assert(dto.authorIfNew == "multi-parent-user")
    assert(child.atumPartitions == (mp1 ++ flagSub))
  }

  test("3-arg single-parent overload with merge = false and copy = true: child is sub only, parent recorded") {
    val agent = new MockedDispatcherAgent
    agent.stubDispatcher()

    val child = agent.getOrCreateAtumSubContext(flagSub, mergeWithParent = false, copyMeasureDefinitions = true)(
      agent.parent(mp1)
    )

    val dto = agent.captured()
    assert(dto.copyMeasureDefinitions)
    assert(dto.partitioning == flagSub.toPartitioningDTO)
    assert(dto.parentPartitionings == Seq(mp1.toPartitioningDTO))
    assert(child.atumPartitions == flagSub)
  }

  test("multi-parent 3-arg overload with copy = true keeps parent order, child identity and author") {
    val agent = new MockedDispatcherAgent
    agent.stubDispatcher()

    val child = agent.getOrCreateAtumSubContext(
      mpChild,
      Seq(mp1, mp2, mp3).map(agent.parent),
      copyMeasureDefinitions = true
    )

    val dto = agent.captured()
    assert(dto.copyMeasureDefinitions)
    assert(dto.parentPartitionings == Seq(mp1, mp2, mp3).map(_.toPartitioningDTO))
    assert(dto.partitioning == mpChild.toPartitioningDTO)
    assert(dto.authorIfNew == "multi-parent-user")
    assert(child.atumPartitions == mpChild)
  }

  test("multi-parent 3-arg overload de-duplicates parents and still carries copy = true") {
    val agent = new MockedDispatcherAgent
    agent.stubDispatcher()
    val parents = Seq(agent.parent(mp1), agent.parent(mp1), agent.parent(mp2))

    agent.getOrCreateAtumSubContext(mpChild, parents, copyMeasureDefinitions = true)

    val dto = agent.captured()
    assert(dto.parentPartitionings == Seq(mp1, mp2).map(_.toPartitioningDTO))
    assert(dto.copyMeasureDefinitions)
  }

  Seq(true, false).foreach { copy =>
    test(s"multi-parent 3-arg overload rejects empty parents without dispatching (copy = $copy)") {
      val agent = new MockedDispatcherAgent

      intercept[IllegalArgumentException] {
        agent.getOrCreateAtumSubContext(mpChild, Seq.empty, copy)
      }
      verify(agent.dispatcher, never()).createPartitioning(any[PartitioningCreateRequest])
    }
  }

  private val defaultIndexRegex = """\$default\$(\d+)""".r

  private def defaultIndexes(methodNames: Array[String], prefix: String): Set[String] =
    methodNames
      .filter(_.startsWith(prefix + "$default$"))
      .flatMap(n => defaultIndexRegex.findFirstMatchIn(n).map(_.group(1)))
      .toSet

  test("getOrCreateAtumSubContext keeps only the $default$2 getter (no default on the new overloads)") {
    val traitIdx = defaultIndexes(classOf[AtumAgent].getMethods.map(_.getName), "getOrCreateAtumSubContext")
    val moduleIdx = defaultIndexes(AtumAgent.getClass.getMethods.map(_.getName), "getOrCreateAtumSubContext")

    assert(traitIdx == Set("2"))
    assert(moduleIdx == Set("2"))
  }

  private def configOf(configValues: Map[String, Any]): Config = {
    val emptyConfig = ConfigFactory.empty()
    configValues.foldLeft(emptyConfig) { case (acc, (configKey, value)) =>
      val configValue = ConfigValueFactory.fromAnyRef(value)
      acc.withValue(configKey, configValue)
    }
  }

}
