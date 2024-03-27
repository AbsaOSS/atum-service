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

package za.co.absa.atum.agent.dispatcher

import com.typesafe.config.Config
import za.co.absa.atum.model.dto._

import java.util.concurrent.atomic.AtomicReference
import java.util.function.UnaryOperator
import scala.collection.immutable.Queue

/**
 *  This dispatcher instead of sending data captures them and stores them in memory.
 *  @param config: Config to be used to create the dispatcher. Keys:
 *           capture-limit - maximal amount of dispatch captures to store.
 */
class CapturingDispatcher(config: Config) extends Dispatcher(config) {
  import CapturingDispatcher._


  val captureLimit: Int = config.getInt(CheckpointLimitKey)

  /**
   * This method is used to clear all captured data.
   */
  def clear(): Queue[CapturedCall] = {
    capturesRef.updateAndGet((_: Queue[CapturedCall]) => Queue.empty)
    captures
  }

  /**
   * This method is used to check if the given function call has been captured.
   *
   * @param functionName - the function name that was supposed to be dispatched
   * @return             - true if the function was captured, false otherwise
   */
  def contains(functionName: String): Boolean = {
    captures.exists(_.functionName == functionName)
  }

  /**
   * This method is used to check if the given function call has been captured.
   *
   * @param functionName - the function name that was supposed to be dispatched
   * @param input        - the input parameter of the function
   * @return             - true if the function was captured, false otherwise
   */
  def contains[I](functionName: String, input: I): Boolean = {
    captures.exists(item => ((item.functionName == functionName) && (item.input == input)))
  }

  /**
   * This method is used to check if the given function call has been captured.
   *
   * @param functionName - the function name that was supposed to be dispatched
   * @param input        - the input parameter of the function
   * @param result       - the result of the function
   * @return             - true if the function was captured, false otherwise
   */
  def contains[I, R](functionName: String, input: I, result: R): Boolean = {
    captures.contains(CapturedCall(functionName, input, result))
  }

  /**
   * This method is used to get the captured data.
   *
   * @return the captured data
   */
  def captures: Queue[CapturedCall] = capturesRef.get()


  private val capturesRef = new AtomicReference(Queue.empty[CapturedCall])

  protected def capture[I, R](fnc: Function1[I, R], input: I, result: R): R = {

    val functionName = Thread.currentThread().getStackTrace()(2).getMethodName
    val capture = CapturedCall(functionName, input, result)

    capturesRef.updateAndGet((queue: Queue[CapturedCall]) => {
      if ((captureLimit > 0) && (queue.size >= captureLimit)) {
        queue.dequeue._2.enqueue(capture)
      } else {
        queue.enqueue(capture)
      }
    })

    result
  }

  /**
   *  This method is used to save checkpoint to server.
   *
   *  @param checkpoint : CheckpointDTO to be saved.
   */
  override protected[agent] def saveCheckpoint(checkpoint: CheckpointDTO): Unit = {
    capture(saveCheckpoint, checkpoint, ())
  }

  /**
   *  This method is used to save the additional data to the server.
   *
   *  @param additionalData the data to be saved.
   */
  override protected[agent] def saveAdditionalData(additionalData: AdditionalDataSubmitDTO): Unit = {
    capture(saveAdditionalData, additionalData, ())
  }

  /**
   * This method is used to ensure the server knows the given partitioning.
   * As a response the `AtumContext` is fetched from the server.
   *
   * @param partitioning  : PartitioningSubmitDTO to be used to ensure server knows the given partitioning.
   * @return AtumContextDTO.
   */
  override protected[agent] def createPartitioning(partitioning: PartitioningSubmitDTO): AtumContextDTO = {
    val result = AtumContextDTO(partitioning.partitioning)
    capture(createPartitioning, partitioning, result)
  }
}

object CapturingDispatcher {
  private val CheckpointLimitKey = "atum.dispatcher.capture.capture-limit"

  abstract class CapturedCall {
    type I
    type R
    val functionName: String
    val input: I
    val result: R
  }

  object CapturedCall {

    final case class CapturedCallImpl[IX, RX] private[dispatcher](functionName: String, input: IX, result: RX)
      extends CapturedCall {
      type I = IX
      type R = RX
    }

    def apply[I, R](functionName: String, input: I, result: R): CapturedCall = {
      CapturedCallImpl(functionName, input, result)
    }
  }
}
