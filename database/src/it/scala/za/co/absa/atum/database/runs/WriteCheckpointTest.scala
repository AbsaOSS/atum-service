/*
 * Copyright 2023 ABSA Group Limited
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

package za.co.absa.atum.database.runs

import za.co.absa.atum.database.balta.DBTestSuite

class WriteCheckpointTest extends DBTestSuite {

  dbTest("Write new checkpoint"){

  }

  dbTest("Checkpoint already exists") {

  }

  dbTest("Partitioning of the checkpoint does not exist") {
    function("runs.write_checkpoint")
      .setParam[String]("Hello")
      .setParam[Int](1)
      .setParam(0L)
      .setParam(true)


  }

}
