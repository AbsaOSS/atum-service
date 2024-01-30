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

object FlywayConfiguration {

  val flywayUrl = "jdbc:postgresql://localhost:5432/atum_db"
  val flywayUser = "postgres"
  val flywayPassword = "postgres"
  val flywayLocations: Seq[String] = Seq("filesystem:database/src/main/postgres")
  val flywaySqlMigrationSuffixes: Seq[String] = Seq(".sql",".ddl")

}
