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

package za.co.absa.atum.reader.server

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import org.scalatest.funsuite.AnyFunSuiteLike

class ServerConfigUnitTests extends AnyFunSuiteLike {

    test("test build from config") {
      val server = "https://rivendell.middleearth.jrrt"
      val config: Config = ConfigFactory.empty()
        .withValue(ServerConfig.HostKey, ConfigValueFactory.fromAnyRef(server))
      val serverConfig = ServerConfig.fromConfig(config)
      assert(serverConfig.host == server)
    }
}
