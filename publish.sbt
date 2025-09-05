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

ThisBuild / organization := "za.co.absa.atum-service"
ThisBuild / organizationName := "ABSA Group Limited"
ThisBuild / organizationHomepage := Some(url("https://www.absa.africa"))

publish / skip := true //skipping publishing of the root of the project, publishing only some submodules

ThisBuild / scmInfo := Some(
  ScmInfo(
    browseUrl = url("https://github.com/AbsaOSS/atum-service/tree/master"),
    connection = "scm:git:git://github.com/AbsaOSS/atum-service.git",
    devConnection = "scm:git:ssh://github.com/AbsaOSS/atum-service.git"
  )
)

ThisBuild / developers := List(
  Developer(
    id = "Zejnilovic",
    name = "Saša Zejnilović",
    email = "sasa.zejnilovic@absa.africa",
    url = url("https://github.com/Zejnilovic")
  ),
  Developer(
    id    = "benedeki",
    name  = "David Benedeki",
    email = "david.benedeki@absa.africa",
    url   = url("https://github.com/benedeki")
  ),
  Developer(
    id = "miroslavpojer",
    name = "Miroslav Pojer",
    email = "miroslav.pojer@absa.africa",
    url = url("https://github.com/miroslavpojer")
  ),
  Developer(
    id = "lsulak",
    name = "Ladislav Sulak",
    email = "ladislav.sulak@absa.africa",
    url = url("https://github.com/lsulak")
  ),
  Developer(
    id = "TebaleloS",
    name = "Tebalelo Sekhula",
    email = "tebalelo.sekhula@absa.africa",
    url = url("https://github.com/TebaleloS")
  ),
  Developer(
    id    = "ABLL526",
    name  = "Liam Leibrandt",
    email = "liam.leibrandt@absa.africa",
    url   = url("https://github.com/ABLL526")
  ),
  Developer(
    id    = "MatloaItumeleng",
    name  = "Itumeleng Matloa",
    email = "itumeleng.matloa@absa.africa",
    url   = url("https://github.com/MatloaItumeleng")
  ),
  Developer(
    id = "salamonpavel",
    name = "Pavel Salamon",
    email = "pavel.salamon@absa.africa",
    url = url("https://github.com/salamonpavel")
  )
)

ThisBuild / description := "Data completeness and accuracy application meant to be used for data processed by Apache Spark"
ThisBuild / startYear := Some(2021)
ThisBuild / licenses += "Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.txt")
ThisBuild / homepage := Some(url("https://github.com/AbsaOSS/atum-service"))
