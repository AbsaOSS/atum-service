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

package za.co.absa.atum.server.api.database

import doobie.Transactor
import za.co.absa.db.fadb.doobie.DoobieEngine
import zio._
import zio.interop.catz._

class PostgresDatabaseProvider(val dbEngine: DoobieEngine[Task])

object PostgresDatabaseProvider {
  val layer: URLayer[Transactor[Task], PostgresDatabaseProvider] = ZLayer {
    for {
      transactor <- ZIO.service[Transactor[Task]]
      doobieEngine <- ZIO.succeed(new DoobieEngine[Task](transactor))
    } yield new PostgresDatabaseProvider(doobieEngine)
  }
}
