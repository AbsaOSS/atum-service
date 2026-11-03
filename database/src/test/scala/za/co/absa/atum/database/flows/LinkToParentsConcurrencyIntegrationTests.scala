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

package za.co.absa.atum.database.flows

import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite

import java.sql.{Connection, DriverManager}
import java.util.{Properties, UUID}
import java.util.concurrent.{Callable, ConcurrentLinkedQueue, CountDownLatch, Executors, TimeUnit}

/**
 *  Two-connection race tests of the global advisory lock taken by `flows._link_to_parents` (LINEAGE-06).
 *
 *  Why this is NOT a `DBTestSuite`: balta runs a whole test in ONE connection and rolls it back at the end. A second
 *  connection could never see that transaction's data and, worse, waiting on its advisory lock would hang the test.
 *  Here every actor has its own JDBC connection (autocommit off) and the data is really COMMITTED, so the tests clean
 *  up after themselves (in `finally` of every test and in `afterAll`) and only use UUID-unique partitionings with the
 *  key `conc`. `Test / parallelExecution := false` runs the suites sequentially; because the committed rows are removed
 *  again, later balta suites that compare global row counts are not poisoned.
 *
 *  The race is forced deterministically: connection A takes the very same advisory lock the SQL uses
 *  (`pg_advisory_xact_lock(hashtextextended('atum.flows.link_to_parents', 0))`, re-entrant for the same session),
 *  re-links P under G and does NOT commit. Connection B creates a child C under P; B has to wait for the lock, which
 *  is observed from a third (monitor) connection in `pg_stat_activity`. The lock is released only when A commits.
 *  After that B must see P under G and therefore put C into G's flow as well.
 *
 *  Negative control: without the advisory lock C is lost from G's flow. That was demonstrated in the research scratch
 *  run (two psql sessions); there is deliberately no "no lock" production mode for testing it here.
 *
 *  The `lock_timeout` on B is only a safety net of the test, it is never part of production code.
 */
class LinkToParentsConcurrencyIntegrationTests extends AnyFunSuite with BeforeAndAfterAll {

  private val createdIds = new ConcurrentLinkedQueue[Long]()

  private lazy val dbProperties: Properties = {
    val props = new Properties()
    val stream = getClass.getClassLoader.getResourceAsStream("database.properties")
    require(stream != null, "database.properties not found on the test classpath")
    try props.load(stream)
    finally stream.close()
    props
  }

  private def newConnection(autoCommit: Boolean): Connection = {
    val connection = DriverManager.getConnection(
      dbProperties.getProperty("test.jdbc.url"),
      dbProperties.getProperty("test.jdbc.username"),
      dbProperties.getProperty("test.jdbc.password")
    )
    connection.setAutoCommit(autoCommit)
    connection
  }

  private def closeQuietly(connection: Connection): Unit =
    if (connection != null) {
      try {
        if (!connection.isClosed) {
          if (!connection.getAutoCommit) connection.rollback()
          connection.close()
        }
      } catch {
        case e: Exception => println(s"Closing a test connection failed: ${e.getMessage}")
      }
    }

  private def uniquePartitioning(tag: String): String =
    s"""{"version":1,"keys":["conc"],"keysToValuesMap":{"conc":"$tag-${UUID.randomUUID()}"}}"""

  /** Calls the 4-arg create function, returns (status, id_partitioning) and remembers the id for the cleanup. */
  private def createWithParents(
    connection: Connection,
    partitioning: String,
    parents: Seq[Long],
    copyMeasureDefinitions: Boolean
  ): (Int, Long) = {
    val statement = connection.prepareStatement(
      "SELECT status, id_partitioning FROM runs.create_partitioning_with_parents(?::jsonb, ?, ?::bigint[], ?)"
    )
    try {
      statement.setString(1, partitioning)
      statement.setString(2, "concurrency-test")
      statement.setArray(3, connection.createArrayOf("bigint", parents.map(Long.box).toArray[AnyRef]))
      statement.setBoolean(4, copyMeasureDefinitions)
      val resultSet = statement.executeQuery()
      try {
        assert(resultSet.next(), "create_partitioning_with_parents returned no row")
        val status = resultSet.getInt("status")
        val id = resultSet.getLong("id_partitioning")
        if (!resultSet.wasNull()) createdIds.add(id)
        (status, id)
      } finally resultSet.close()
    } finally statement.close()
  }

  private def ancestors(connection: Connection, id: Long): Set[Long] = {
    val statement = connection.prepareStatement(
      "SELECT ancestor_id FROM runs.get_partitioning_ancestors(?, 1000, 0)"
    )
    try {
      statement.setLong(1, id)
      val resultSet = statement.executeQuery()
      try {
        var result = Set.empty[Long]
        while (resultSet.next()) {
          val ancestorId = resultSet.getLong("ancestor_id")
          if (!resultSet.wasNull()) result += ancestorId
        }
        result
      } finally resultSet.close()
    } finally statement.close()
  }

  private def scalarLong(connection: Connection, sql: String): Long = {
    val statement = connection.createStatement()
    try {
      val resultSet = statement.executeQuery(sql)
      try {
        resultSet.next()
        resultSet.getLong(1)
      } finally resultSet.close()
    } finally statement.close()
  }

  /** Waits (bounded) until at least one backend waits on an advisory lock, as seen from the monitor connection. */
  private def waitForAdvisoryLockWaiter(monitor: Connection, deadlineMillis: Long): Boolean = {
    val sql = "SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock' AND wait_event = 'advisory' " +
      "AND datname = current_database() AND pid <> pg_backend_pid()"
    val deadline = System.currentTimeMillis() + deadlineMillis
    var waiting = false
    while (!waiting && System.currentTimeMillis() < deadline) {
      waiting = scalarLong(monitor, sql) >= 1
      if (!waiting) Thread.sleep(50)
    }
    waiting
  }

  /**
   *  Removes every committed row created by this suite (tracked ids plus any leftover `conc` partitioning from an
   *  earlier aborted run). All deletes are attempted, the first failure is rethrown afterwards.
   */
  private def cleanup(): Unit = {
    val connection = newConnection(autoCommit = true)
    var failure: Option[Exception] = None
    try {
      val ids = new java.util.LinkedHashSet[java.lang.Long]()
      createdIds.forEach(id => ids.add(id))
      val select = connection.createStatement()
      try {
        val resultSet = select.executeQuery(
          "SELECT id_partitioning FROM runs.partitionings WHERE partitioning->'keysToValuesMap'->>'conc' IS NOT NULL"
        )
        try {
          while (resultSet.next()) ids.add(resultSet.getLong(1))
        } finally resultSet.close()
      } finally select.close()

      if (!ids.isEmpty) {
        val idArray = ids.toArray[AnyRef](new Array[AnyRef](0))
        val deletes = Seq(
          "DELETE FROM flows.partitioning_to_flow WHERE fk_partitioning = ANY(?) OR fk_flow IN " +
            "(SELECT id_flow FROM flows.flows WHERE fk_primary_partitioning = ANY(?))",
          "DELETE FROM flows.flows WHERE fk_primary_partitioning = ANY(?)",
          "DELETE FROM runs.measure_definitions WHERE fk_partitioning = ANY(?)",
          "DELETE FROM runs.additional_data WHERE fk_partitioning = ANY(?)",
          "DELETE FROM runs.partitionings WHERE id_partitioning = ANY(?)"
        )
        deletes.foreach { sql =>
          try {
            val statement = connection.prepareStatement(sql)
            try {
              val parameterCount = sql.count(_ == '?')
              (1 to parameterCount).foreach(i => statement.setArray(i, connection.createArrayOf("bigint", idArray)))
              statement.executeUpdate()
            } finally statement.close()
          } catch {
            case e: Exception =>
              println(s"Cleanup statement failed: $sql -> ${e.getMessage}")
              if (failure.isEmpty) failure = Some(e)
          }
        }
      }
      createdIds.clear()
    } finally closeQuietly(connection)
    failure.foreach(e => throw e)
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    cleanup() // leftovers of an earlier aborted run
  }

  override protected def afterAll(): Unit = {
    try cleanup()
    finally super.afterAll()
  }

  test("child created under P while P is re-linked under G ends up in G's flow (LINEAGE-06)") {
    val executor = Executors.newSingleThreadExecutor()
    var setup: Connection = null
    var connA: Connection = null
    var connB: Connection = null
    var monitor: Connection = null
    try {
      setup = newConnection(autoCommit = true)
      val partitioningP = uniquePartitioning("P")
      val partitioningG = uniquePartitioning("G")
      val partitioningC = uniquePartitioning("C")
      val (_, idP) = createWithParents(setup, partitioningP, Seq.empty, copyMeasureDefinitions = false)
      val (_, idG) = createWithParents(setup, partitioningG, Seq.empty, copyMeasureDefinitions = false)

      // A: hold the global linking lock and re-link P under G without committing
      connA = newConnection(autoCommit = false)
      connA
        .createStatement()
        .execute("SELECT pg_advisory_xact_lock(hashtextextended('atum.flows.link_to_parents', 0))")
      val (statusA, _) = createWithParents(connA, partitioningP, Seq(idG), copyMeasureDefinitions = false)
      assert(statusA == 13, "re-linking the existing P under G must report 13 (existing, link written)")

      // B: create the child C under P in another thread, it has to wait for the lock held by A
      connB = newConnection(autoCommit = false)
      connB.createStatement().execute("SET lock_timeout = '20s'") // safety net of the test only
      val futureB = executor.submit(new Callable[(Int, Long)] {
        override def call(): (Int, Long) =
          createWithParents(connB, partitioningC, Seq(idP), copyMeasureDefinitions = false)
      })

      monitor = newConnection(autoCommit = true)
      val gated = waitForAdvisoryLockWaiter(monitor, deadlineMillis = 10000)
      assert(gated, "the creation of C did not wait on the advisory lock (no pg_stat_activity waiter) within 10 s")
      assert(!futureB.isDone, "the creation of C must still be blocked while A holds the lock")

      connA.commit()
      val (statusB, idC) = futureB.get(30, TimeUnit.SECONDS)
      assert(statusB == 12, "C is a new child with a parent, so the status must be 12")
      connB.commit()

      assert(ancestors(setup, idC) == Set(idP, idG), "C must be in the flow of G as well (ancestors(C) = {P, G})")
      assert(ancestors(setup, idP) == Set(idG))
    } finally {
      try {
        closeQuietly(connA)
        closeQuietly(connB)
        closeQuietly(monitor)
        closeQuietly(setup)
      } finally {
        executor.shutdownNow()
        cleanup()
      }
    }
  }

  test("two identical racing creates: exactly one creates, the other finds the existing one (LINEAGE-06)") {
    val executor = Executors.newFixedThreadPool(2)
    var connA: Connection = null
    var connB: Connection = null
    var check: Connection = null
    try {
      connA = newConnection(autoCommit = false)
      connB = newConnection(autoCommit = false)
      check = newConnection(autoCommit = true)
      val partitioning = uniquePartitioning("race")
      val startGate = new CountDownLatch(1)

      def racer(connection: Connection): Callable[(Int, Long)] = new Callable[(Int, Long)] {
        override def call(): (Int, Long) = {
          assert(startGate.await(30, TimeUnit.SECONDS), "start gate was not opened")
          val result = createWithParents(connection, partitioning, Seq.empty, copyMeasureDefinitions = false)
          connection.commit()
          result
        }
      }

      val futureA = executor.submit(racer(connA))
      val futureB = executor.submit(racer(connB))
      startGate.countDown()
      val (statusA, idA) = futureA.get(30, TimeUnit.SECONDS)
      val (statusB, idB) = futureB.get(30, TimeUnit.SECONDS)

      val statuses = Seq(statusA, statusB)
      assert(statuses.count(Set(11, 12)) == 1, s"exactly one creation expected, statuses: $statuses")
      assert(statuses.count(Set(13, 14)) == 1, s"exactly one 'already exists' expected, statuses: $statuses")
      assert(idA == idB, "both calls must return the id of the same partitioning")

      val statement = check.prepareStatement("SELECT count(*) FROM runs.partitionings WHERE partitioning = ?::jsonb")
      try {
        statement.setString(1, partitioning)
        val resultSet = statement.executeQuery()
        try {
          assert(resultSet.next())
          assert(resultSet.getLong(1) == 1L, "exactly one partitionings row expected")
        } finally resultSet.close()
      } finally statement.close()
    } finally {
      try {
        closeQuietly(connA)
        closeQuietly(connB)
        closeQuietly(check)
      } finally {
        executor.shutdownNow()
        cleanup()
      }
    }
  }
}
