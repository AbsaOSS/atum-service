package za.co.absa.atum.server.api.http

import za.co.absa.atum.server.api.controller.{CheckpointController, PartitioningController}
import zio.RIO
import zio.metrics.connectors.prometheus.PrometheusPublisher

trait HttpEnv {

  type Env = PartitioningController with CheckpointController with PrometheusPublisher
  type F[A] = RIO[Env, A]

}
