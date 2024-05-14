package za.co.absa.atum.server.api.database.runs.functions

import doobie.util.Read
import org.scalatest.funsuite.AnyFunSuiteLike
import za.co.absa.atum.server.model.CheckpointMeasurements

class GetPartitioningCheckpointsSpec extends AnyFunSuiteLike {

  test("Read[CheckpointMeasurements] test") {
    import doobie.postgres.implicits._
    import doobie.postgres.circe.jsonb.implicits.jsonbGet
    import za.co.absa.atum.server.api.database.DoobieImplicits.Sequence._
    Read[CheckpointMeasurements]
  }

}
