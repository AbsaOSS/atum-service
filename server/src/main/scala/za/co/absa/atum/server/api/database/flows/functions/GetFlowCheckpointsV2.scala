package za.co.absa.atum.server.api.database.flows.functions

import doobie.implicits.toSqlInterpolator
import za.co.absa.atum.server.api.database.PostgresDatabaseProvider
import za.co.absa.atum.server.api.database.flows.Flows
import za.co.absa.atum.server.api.database.flows.functions.GetFlowCheckpointsV2.GetFlowCheckpointsArgs
import za.co.absa.atum.server.model.{CheckpointFromDB, CheckpointItemFromDB}
import za.co.absa.db.fadb.DBSchema
import za.co.absa.db.fadb.doobie.DoobieEngine
import za.co.absa.db.fadb.doobie.DoobieFunction.DoobieMultipleResultFunctionWithAggStatus
import za.co.absa.db.fadb.status.aggregation.implementations.ByFirstErrorStatusAggregator
import za.co.absa.db.fadb.status.handling.implementations.StandardStatusHandling
import zio._
import za.co.absa.db.fadb.doobie.postgres.circe.implicits.jsonbGet


class GetFlowCheckpointsV2 (implicit schema: DBSchema, dbEngine: DoobieEngine[Task])
  extends DoobieMultipleResultFunctionWithAggStatus[GetFlowCheckpointsArgs, Option[CheckpointItemFromDB], Task](values =>
    Seq(
      fr"${values.partitioningId}",
      fr"${values.limit}",
      fr"${values.checkpointName}",
      fr"${values.offset}"
    )
  )
    with StandardStatusHandling
    with ByFirstErrorStatusAggregator {

  override def fieldsToSelect: Seq[String] = super.fieldsToSelect ++ Seq(
    "id_checkpoint",
    "checkpoint_name",
    "author",
    "measured_by_atum_agent",
    "measure_name",
    "measured_columns",
    "measurement_value",
    "checkpoint_start_time",
    "checkpoint_end_time",
    "has_more"
  )
}

object GetFlowCheckpointsV2 {
  case class GetFlowCheckpointsArgs(
     partitioningId: Long,
     limit: Option[Int],
     offset: Option[Long],
     checkpointName: Option[String]
  )

  val layer: URLayer[PostgresDatabaseProvider, GetFlowCheckpointsV2] = ZLayer {
    for {
      dbProvider <- ZIO.service[PostgresDatabaseProvider]
    } yield new GetFlowCheckpointsV2()(Flows, dbProvider.dbEngine)
  }
}
