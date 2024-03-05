package za.co.absa.atum.server.api.http

import sttp.tapir.server.metrics.prometheus.PrometheusMetrics

object Metrics extends HttpEnv {

  // 1. Define metrics of your interest
  val prometheusMetrics: PrometheusMetrics[F] = PrometheusMetrics[F]("atum")
    .addRequestsTotal()
    .addRequestsActive()
    .addRequestsDuration()
  //    .addCustom()

}
