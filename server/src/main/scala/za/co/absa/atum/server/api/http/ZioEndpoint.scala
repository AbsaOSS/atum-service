package za.co.absa.atum.server.api.http

import sttp.tapir.{PublicEndpoint, endpoint, stringBody}
import zio._
import zio.metrics.connectors.MetricsConfig
import zio.metrics.connectors.prometheus.{PrometheusPublisher, prometheusLayer, publisherLayer}
import zio.metrics.jvm
import zio.metrics.jvm.{BufferPools, ClassLoading, DefaultJvmMetrics, GarbageCollector, MemoryAllocation, MemoryPools, Standard, VersionInfo}

object ZioEndpoint {

  /** DefaultJvmMetrics.live.orDie >+> is optional if you want JVM metrics */
  val layer = DefaultJvmMetrics.live.orDie >+> ZLayer.make[PrometheusPublisher](
    ZLayer.succeed(MetricsConfig(5.seconds)),
    prometheusLayer,
    publisherLayer
  )

  private val unsafeLayers = Unsafe.unsafe { implicit u =>
    Runtime.unsafe.fromLayer(layer)
  }

//  def getMetricsEffect: ZIO[Any, Nothing, String] =
//    Unsafe.unsafe { implicit u =>
//      unsafeLayers.run(ZIO
//        .serviceWithZIO[PrometheusPublisher](_.get)
//      )
//    }

//  def getMetricsEffect: ZIO[PrometheusPublisher, Nothing, String] =
//    ZIO.serviceWithZIO[PrometheusPublisher](_.get)

  val zioEndpoint: PublicEndpoint[Unit, Unit, String, Any] =
    endpoint.get.in("/zio-metrics").out(stringBody)//.serverLogicSuccess(_ => getMetricsEffect)
}
