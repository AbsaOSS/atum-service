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

package za.co.absa.atum.database.balta.classes

import za.co.absa.atum.database.balta.classes.setter.{Params, SetterFnc}
import za.co.absa.atum.database.balta.classes.setter.Params.NamedParams

case class DBTable(tableName: String) extends DBQuerySupport{

  def insert(values: Params)(implicit connection: DBConnection): QueryResultRow = {
    val columns = values.keys.map {keys =>
      val keysString = keys.mkString(",")
      s"($keysString)"
    }.getOrElse("")
    val paramStr = List.fill(values.size)("?").mkString(",")
    val sql = s"INSERR INTO $tableName $columns VALUES($paramStr) RETURNING *;"
    runQuery(sql, values.setters){_.next()}
  }

// TOOO
//  def fieldValue[K: AllowedParamTypes, T](keyName: String, keyValue: K, fieldName: String)(implicit connection: DBConnection): Option[T] = {
//    ???
//  }

  def where[R](params: NamedParams)(verify: QueryResult => R)(implicit connection: DBConnection): R = {
    composeAndRun(strToOption(paramsToWhereCondition(params)), None, params.setters)(verify)
  }

  def where[R](params: NamedParams, orderBy: String)(verify: QueryResult => R)(implicit connection: DBConnection): R = {
    composeAndRun(strToOption(paramsToWhereCondition(params)), strToOption(orderBy), params.setters)(verify)
  }

  def where[R](condition: String)(verify: QueryResult => R)(implicit connection: DBConnection): R = {
    composeAndRun(strToOption(condition), None)(verify)
  }

  def where[R](condition: String, orderBy: String)(verify: QueryResult => R)(implicit connection: DBConnection): R = {
    composeAndRun(strToOption(condition), strToOption(orderBy))(verify)
  }

  def all[R]()(verify: QueryResult => R)(implicit connection: DBConnection): R = {
    composeAndRun(None, None)(verify)
  }

  def all[R](orderBy: String)(verify: QueryResult => R)(implicit connection: DBConnection): R = {
    composeAndRun(None, strToOption(orderBy))(verify)
  }

  private def composeAndRun[R](whereCondition: Option[String], orderByExpr: Option[String], setters: List[SetterFnc] = List.empty)
                              (verify: QueryResult => R)
                              (implicit connection: DBConnection): R = {
    val where = whereCondition.map("WHERE " + _).getOrElse("")
    val orderBy = orderByExpr.map("ORDER BY " + _).getOrElse("")
    val sql = s"SELECT * FROM $tableName $where $orderBy;"
    runQuery(sql, setters)(verify)
  }

  private def strToOption(str: String): Option[String] = {
    if (str.isEmpty) {
      None
    } else {
      Option(str)
    }
  }

  private def paramsToWhereCondition(params: NamedParams): String = {
    params.definedKeys.foldLeft(List.empty[String]) {(acc, fieldName) =>
      s"$fieldName = ?" :: acc
    }.mkString(" AND ")
  }
}