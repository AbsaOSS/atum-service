/*
 * Copyright 2021 ABSA Group Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package za.co.absa.atum.web.api.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import za.co.absa.atum.web.api.NotFoundException
import za.co.absa.atum.web.dao.ApiModelDao
import za.co.absa.atum.model.Flow

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


@Service
class FlowService @Autowired()(dao: ApiModelDao[Flow]) extends BaseApiService[Flow](dao) {
  override val entityName: String = "Flow"

  def withFlowExistsF[S](flowId: UUID)(fn: => Future[S]): Future[S] = {
    val check: Future[Unit] = for {
      flowExists <- this.exists(flowId)
      _ = if (!flowExists) throw NotFoundException(s"Referenced flow (flowId=$flowId) was not found.")
    } yield ()

    check.flatMap(_ => fn)
  }
}
