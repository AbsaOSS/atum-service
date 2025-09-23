// JacocoBaseKeysPlugin.scala | last modified in v1.0.0

import sbt.*
import sbt.Keys.*

object JacocoBaseKeysPlugin extends AutoPlugin {
  object autoImport {
    val jacocoPluginEnabled = settingKey[Boolean]("Marker for JaCoCo plugin participation")
    val jacocoClean         = taskKey[Unit]("Clean JaCoCo outputs")
    val jacocoReport        = taskKey[File]("Generate per-module JaCoCo report")
  }
  import autoImport.*

  // apply to every project (project scope → target.value etc. are valid)
  override def trigger  = allRequirements
  override def requires = plugins.JvmPlugin

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    jacocoPluginEnabled := false,                             // default: not participating
    jacocoClean := { streams.value.log.debug("[jacoco] not enabled here; clean no-op.") },
    jacocoReport := {
      val d = target.value / "jacoco" / "report"              // safe placeholder dir
      IO.createDirectory(d)
      streams.value.log.debug("[jacoco] not enabled here; report no-op.")
      d
    }
  )
}
