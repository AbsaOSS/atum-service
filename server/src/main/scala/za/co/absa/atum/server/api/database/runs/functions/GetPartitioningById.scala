package za.co.absa.atum.server.api.database.runs.functions

import doobie.implicits.toPutOps
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.api.database.runs.Runs
import za.co.absa.db.fadb.DBSchema
import za.co.absa.db.fadb.doobie.DoobieEngine
import za.co.absa.db.fadb.doobie.DoobieFunction.DoobieSingleResultFunctionWithStatus
import za.co.absa.db.fadb.status.handling.implementations.StandardStatusHandling
import za.co.absa.atum.server.model.PartitioningFromDB
import zio._
import za.co.absa.atum.server.api.database.DoobieImplicits.Sequence.get
import doobie.postgres.implicits._
import za.co.absa.db.fadb.doobie.postgres.circe.implicits.jsonbGet

class GetPartitioningById(implicit schema: DBSchema, dbEngine: DoobieEngine[Task])
    extends DoobieSingleResultFunctionWithStatus[Long, PartitioningFromDB, Task](values => Seq(fr"${values}"))
    with StandardStatusHandling {

  override def fieldsToSelect: Seq[String] =
    super.fieldsToSelect ++ Seq("id", "partitioning", "parent_partitioning", "author")
}

object GetPartitioningById {
  val layer: URLayer[PostgresDatabaseProvider, GetPartitioningById] = ZLayer {
    for {
      dbProvider <- ZIO.service[PostgresDatabaseProvider]
    } yield new GetPartitioningById()(Runs, dbProvider.dbEngine)
  }
}
