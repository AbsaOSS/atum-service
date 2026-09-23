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

package za.co.absa.atum.agent.core

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.{Level, LoggerContext}
import ch.qos.logback.core.read.ListAppender
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.slf4j.helpers.{NOPLogger, NOPLoggerFactory}
import org.slf4j.{ILoggerFactory, Logger, LoggerFactory}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}
import java.lang.reflect.Modifier
import scala.jdk.CollectionConverters._

object LoggingUnitTests {
  object LoggingObject extends Logging {
    def emit(): Unit = log.info("from an object")
    def logger: Logger = log
  }

  class LoggingClass extends Logging {
    def emit(): Unit = log.warn("from a class")
    def logger: Logger = log
  }

  // Serializability matters because anything mixing `Logging` in may end up captured by a Spark closure.
  class SerializableLogging extends Logging with Serializable {
    def emit(): Unit = log.info("after round trip")
    def logger: Logger = log
  }
}

class LoggingUnitTests extends AnyFlatSpec with Matchers {
  import LoggingUnitTests._

  private val loggerFactory: ILoggerFactory = LoggerFactory.getILoggerFactory

  /**
   *  Which slf4j binding wins is decided by whatever the Spark runtime puts on the classpath - Spark 3.5
   *  brings the log4j2 bridge, while the Spark 4 row ends up on logback. Message level capture is therefore
   *  only attempted when logback is the bound provider; every other assertion in this suite is deliberately
   *  binding agnostic, because that portability is exactly what the slf4j migration is meant to buy.
   */
  private def logbackContext: Option[LoggerContext] = loggerFactory match {
    case context: LoggerContext => Some(context)
    case _ => None
  }

  private def withCapturedLogs(context: LoggerContext, loggerName: String)(body: => Unit): Seq[ILoggingEvent] = {
    val logger = context.getLogger(loggerName)
    val appender = new ListAppender[ILoggingEvent]()
    appender.setContext(context)
    appender.start()

    val originalLevel = logger.getLevel
    logger.setLevel(Level.TRACE)
    logger.addAppender(appender)
    try {
      body
      appender.list.asScala.toSeq
    } finally {
      logger.detachAppender(appender)
      logger.setLevel(originalLevel)
      appender.stop()
    }
  }

  "Logging" should "name the logger after the implementing class" in {
    new LoggingClass().logger.getName shouldBe "za.co.absa.atum.agent.core.LoggingUnitTests$LoggingClass"
  }

  it should "strip the trailing dollar sign from object names" in {
    LoggingObject.logger.getName shouldBe "za.co.absa.atum.agent.core.LoggingUnitTests$LoggingObject"
    LoggingObject.logger.getName should not endWith "$"
  }

  it should "bind to a real slf4j implementation rather than to the no-op fallback" in {
    // A missing binding degrades to NOPLogger and silently swallows every agent log line - the exact failure
    // mode to watch for when the agent is dropped onto an unfamiliar Spark runtime such as AWS Glue.
    val logger = new LoggingClass().logger

    logger should not be a[NOPLogger]
    loggerFactory should not be a[NOPLoggerFactory]
  }

  it should "emit through the bound backend without throwing on any level" in {
    val instance = new LoggingClass()
    noException should be thrownBy {
      instance.logger.trace("trace")
      instance.logger.debug("debug")
      instance.logger.info("info")
      instance.logger.warn("warn")
      instance.logger.error("error")
    }
  }

  it should "hold the logger in a transient field so implementors stay serializable" in {
    val loggerFields = classOf[SerializableLogging].getDeclaredFields
      .filter(field => classOf[Logger].isAssignableFrom(field.getType))
      .toSeq

    loggerFields should not be empty
    all(loggerFields.map(field => Modifier.isTransient(field.getModifiers))) shouldBe true
  }

  it should "survive java serialization and keep logging afterwards" in {
    val original = new SerializableLogging
    original.emit() // force the lazy logger to initialise, so a non-transient field would break serialization

    val bytes = {
      val buffer = new ByteArrayOutputStream()
      val out = new ObjectOutputStream(buffer)
      try out.writeObject(original)
      finally out.close()
      buffer.toByteArray
    }

    val in = new ObjectInputStream(new ByteArrayInputStream(bytes))
    val restored =
      try in.readObject().asInstanceOf[SerializableLogging]
      finally in.close()

    restored.logger.getName shouldBe "za.co.absa.atum.agent.core.LoggingUnitTests$SerializableLogging"
    noException should be thrownBy restored.emit()
  }

  it should "be backed by slf4j rather than by Spark's internal Logging trait" in {
    val interfaces = classOf[LoggingClass].getInterfaces.map(_.getName)
    interfaces should contain("za.co.absa.atum.agent.core.Logging")
    interfaces should not contain "org.apache.spark.internal.Logging"

    classOf[Logger].isAssignableFrom(new LoggingClass().logger.getClass) shouldBe true
  }

  it should "deliver the formatted message and level to the backend" in {
    logbackContext match {
      case None =>
        cancel(s"the bound slf4j provider is ${loggerFactory.getClass.getName}, not logback - capture skipped")
      case Some(context) =>
        val fromClass = withCapturedLogs(context, "za.co.absa.atum.agent.core.LoggingUnitTests$LoggingClass") {
          new LoggingClass().emit()
        }
        fromClass.map(_.getFormattedMessage) shouldBe Seq("from a class")
        fromClass.map(_.getLevel) shouldBe Seq(Level.WARN)

        val fromObject = withCapturedLogs(context, "za.co.absa.atum.agent.core.LoggingUnitTests$LoggingObject") {
          LoggingObject.emit()
        }
        fromObject.map(_.getFormattedMessage) shouldBe Seq("from an object")
        fromObject.map(_.getLevel) shouldBe Seq(Level.INFO)
    }
  }
}
