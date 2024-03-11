package za.co.absa.atum.server.api.database.runs.functions

import doobie.Fragment
import doobie.implicits.toSqlInterpolator
import doobie.util.Read
import play.api.libs.json.Json
import za.co.absa.atum.model.dto.PartitioningSubmitDTO
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.model.PartitioningForDB
import za.co.absa.fadb.DBSchema
import za.co.absa.fadb.doobie.{DoobieEngine, StatusWithData}
import za.co.absa.fadb.doobie.DoobieFunction.DoobieSingleResultFunctionWithStatus
import za.co.absa.fadb.status.handling.implementations.StandardStatusHandling
import za.co.absa.atum.server.api.database.runs.Runs
import zio._
import zio.interop.catz._

class GetPartitioningMeasures (implicit schema: DBSchema, dbEngine: DoobieEngine[Task])
  extends DoobieSingleResultFunctionWithStatus[PartitioningSubmitDTO, Unit, Task]
    with StandardStatusHandling {

  override def sql(values: PartitioningSubmitDTO)(implicit read: Read[StatusWithData[Unit]]): Fragment = {
    val partitioning = PartitioningForDB.fromSeqPartitionDTO(values.partitioning)
    val partitioningJsonString = Json.toJson(partitioning).toString

    sql"""SELECT ${Fragment.const(selectEntry)} FROM ${Fragment.const(functionName)}(
                  ${
                    import za.co.absa.atum.server.api.database.DoobieImplicits.Jsonb.jsonbPutUsingString
                    partitioningJsonString
                  }
                ) ${Fragment.const(alias)};"""

  }

}

object GetPartitioningMeasures {
  val layer: URLayer[PostgresDatabaseProvider, GetPartitioningMeasures] = ZLayer {
    for {
      dbProvider <- ZIO.service[PostgresDatabaseProvider]
    } yield new GetPartitioningMeasures()(Runs, dbProvider.dbEngine)
  }
}

