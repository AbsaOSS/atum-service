package za.co.absa.atum.server.api.database.runs.functions

import doobie.util.Read
import org.scalatest.funsuite.AnyFunSuiteLike

class GetPartitioningCheckpointsSpec extends AnyFunSuiteLike {

  test("Read[ZonedDateTime] test") {
    import doobie.postgres.implicits._
    Read[java.time.ZonedDateTime]
  }

}
