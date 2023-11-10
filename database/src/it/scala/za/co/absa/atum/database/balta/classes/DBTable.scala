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

import za.co.absa.atum.database.balta.classes.setter.{AllowedParamTypes, Params, SetterFnc}
import za.co.absa.atum.database.balta.classes.setter.Params.NamedParams

case class DBTable(tableName: String) extends DBQuerySupport{

  def insert(values: Params)(implicit connection: DBConnection): QueryResultRow = {
    val columns = values.keys.map {keys =>
      val keysString = keys.mkString(",") // TODO https://github.com/AbsaOSS/balta/issues/2
      s"($keysString)"
    }.getOrElse("")
    val paramStr = values.setters.map(_.sqlEntry).mkString(",")
    val sql = s"INSERT INTO $tableName $columns VALUES($paramStr) RETURNING *;"
    runQuery(sql, values.setters){_.next()}
  }

  def fieldValue[K: AllowedParamTypes, T](keyName: String, keyValue: K, fieldName: String)
                                         (implicit connection: DBConnection): Option[Option[T]] = {
    where(Params.add(keyName, keyValue)){resultSet =>
      if (resultSet.hasNext) {
        Some(resultSet.next().getAs[T](fieldName))
      } else {
        None
      }
    }
  }

  def where[R](params: NamedParams)(verify: QueryResult => R)(implicit connection: DBConnection): R = {
    composeSelectAndRun(strToOption(paramsToWhereCondition(params)), None, params.setters)(verify)
  }

  def where[R](params: NamedParams, orderBy: String)(verify: QueryResult => R)(implicit connection: DBConnection): R = {
    composeSelectAndRun(strToOption(paramsToWhereCondition(params)), strToOption(orderBy), params.setters)(verify)
  }

  def where[R](condition: String)(verify: QueryResult => R)(implicit connection: DBConnection): R = {
    composeSelectAndRun(strToOption(condition), None)(verify)
  }

  def where[R](condition: String, orderBy: String)(verify: QueryResult => R)(implicit connection: DBConnection): R = {
    composeSelectAndRun(strToOption(condition), strToOption(orderBy))(verify)
  }

  def all[R]()(verify: QueryResult => R)(implicit connection: DBConnection): R = {
    composeSelectAndRun(None, None)(verify)
  }

  def all[R](orderBy: String)(verify: QueryResult => R)(implicit connection: DBConnection): R = {
    composeSelectAndRun(None, strToOption(orderBy))(verify)
  }

  def count()(implicit connection: DBConnection): Long = {
    composeCountAndRun(None)
  }

  def count(params: NamedParams)(implicit connection: DBConnection): Long = {
    composeCountAndRun(strToOption(paramsToWhereCondition(params)), params.setters)
  }

  def count(condition: String)(implicit connection: DBConnection): Long = {
    composeCountAndRun(strToOption(condition))
  }

  private def composeSelectAndRun[R](whereCondition: Option[String], orderByExpr: Option[String], setters: List[SetterFnc] = List.empty)
                              (verify: QueryResult => R)
                              (implicit connection: DBConnection): R = {
    val where = whereCondition.map("WHERE " + _).getOrElse("")
    val orderBy = orderByExpr.map("ORDER BY " + _).getOrElse("")
    val sql = s"SELECT * FROM $tableName $where $orderBy;"
    runQuery(sql, setters)(verify)
  }

  private def composeCountAndRun(whereCondition: Option[String], setters: List[SetterFnc] = List.empty)
                                (implicit connection: DBConnection): Long = {
    val where = whereCondition.map("WHERE " + _).getOrElse("")
    val sql = s"SELECT count(1) AS cnt FROM $tableName $where;"
    runQuery(sql, setters) {resultSet =>
      resultSet.next().getLong("cnt").getOrElse(0)
    }
  }

  private def strToOption(str: String): Option[String] = {
    if (str.isEmpty) {
      None
    } else {
      Option(str)
    }
  }

  private def paramsToWhereCondition(params: NamedParams): String = {
    params.pairs.foldLeft(List.empty[String]) {case (acc, (fieldName, setterFnc)) =>
      s"$fieldName = ${setterFnc.sqlEntry}" :: acc // TODO https://github.com/AbsaOSS/balta/issues/2
    }.mkString(" AND ")
  }
}
