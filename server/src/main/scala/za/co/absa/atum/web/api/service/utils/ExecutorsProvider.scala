package za.co.absa.atum.web.api.service.utils

import java.util.concurrent.Executor
import scala.concurrent.ExecutionContext

trait ExecutorsProvider {

  def ioBoundExecutionContext: ExecutionContext
  def ioBoundExecutor: Executor

  def cpuBoundExecutionContext: ExecutionContext
  def cpuBoundExecutor: Executor

}
