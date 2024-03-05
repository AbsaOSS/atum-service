package za.co.absa.atum.server.monitoring

import za.co.absa.atum.server.config.JvmMonitoringConfig
import zio.metrics.connectors.{MetricsConfig, prometheus}
import zio.metrics.jvm.DefaultJvmMetrics
import zio._
import zio.metrics.connectors.prometheus.PrometheusPublisher

object JvmMonitoring {

  private def jvmMonitoringLayer(jvmMonitoringConfig: JvmMonitoringConfig): ZLayer[Any with MetricsConfig with PrometheusPublisher, Throwable, PrometheusPublisher with Unit with MetricsConfig] = {
    prometheus.publisherLayer ++
      prometheus.prometheusLayer ++
      Runtime.enableRuntimeMetrics ++
      DefaultJvmMetrics.live.unit ++
      ZLayer.succeed(MetricsConfig(java.time.Duration.ofSeconds(jvmMonitoringConfig.intervalInSeconds)))
  }

//  val layer: ZLayer[Any, Config.Error, PrometheusPublisher with Unit with MetricsConfig] =
//    ZLayer {
//      for {
//        jvmMonitoringConfig <- ZIO.config[JvmMonitoringConfig](JvmMonitoringConfig.config)
//      } yield {
//        if (jvmMonitoringConfig.enabled) {
//          jvmMonitoringLayer(jvmMonitoringConfig)
//        } else {
//          ZLayer.empty
//        }
//      }
//    }

//  val layer: ZLayer[Any, Config.Error, PrometheusPublisher with Unit with MetricsConfig] =
//    ZLayer.fromZIO {
//      for {
//        jvmMonitoringConfig <- ZIO.config[JvmMonitoringConfig](JvmMonitoringConfig.config)
//      } yield {
//        if (jvmMonitoringConfig.enabled) {
//          jvmMonitoringLayer(jvmMonitoringConfig)
//        } else {
//          ZLayer.succeed(())
//        }
//      }
//    }.flatten

}
