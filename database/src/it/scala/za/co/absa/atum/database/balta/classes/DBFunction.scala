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

package za.co.absa.atum.database.balta.classes

import za.co.absa.atum.database.balta.classes.DBFunction.{DBFunctionWithNamedParamsToo, DBFunctionWithPositionedParamsOnly}
import za.co.absa.atum.database.balta.classes.setter.{AllowedParamTypes, SetterFnc}

import scala.collection.immutable.ListMap

sealed abstract class DBFunction private(functionName: String,
                                         params: ListMap[Either[Int, String], SetterFnc]) extends DBQuerySupport {

  private def sql(orderBy: String): String = {
    val paramEntries = params.map{case(key, setterFnc) =>
      key match {
        case Left(_) => setterFnc.sqlEntry
        case Right(name) => s"$name := ${setterFnc.sqlEntry}" // TODO https://github.com/AbsaOSS/balta/issues/2
      }
    }
    val paramsLine = paramEntries.mkString(",")
    s"SELECT * FROM $functionName($paramsLine) $orderBy"
  }

  def execute[R](verify: QueryResult => R /* Assertion */)(implicit connection: DBConnection): R = {
    execute("")(verify)
  }

  def execute[R](orderBy: String)(verify: QueryResult => R /* Assertion */)(implicit connection: DBConnection): R = {
    val orderByPart = if (orderBy.nonEmpty) {s"ORDER BY $orderBy"} else ""
    runQuery(sql(orderByPart), params.values.toList)(verify)
  }

  def setParam[T: AllowedParamTypes](paramName: String, value: T): DBFunctionWithNamedParamsToo = {
    val key = Right(paramName) // TODO normalization TODO https://github.com/AbsaOSS/balta/issues/1
    val fnc = SetterFnc.createSetterFnc(value)
    DBFunctionWithNamedParamsToo(functionName, params + (key, fnc))
  }

  def setParamNull(paramName: String): DBFunctionWithPositionedParamsOnly = {
    val key = Right(paramName) // TODO normalization TODO https://github.com/AbsaOSS/balta/issues/1
    val fnc = SetterFnc.nullSetterFnc
    DBFunctionWithPositionedParamsOnly(functionName, params + (key, fnc))
  }

  def clear(): DBFunctionWithPositionedParamsOnly = {
    DBFunctionWithPositionedParamsOnly(functionName)
  }
}


object DBFunction {

  type ParamsMap = ListMap[Either[Int, String], SetterFnc]
  def apply(functionName: String): DBFunctionWithPositionedParamsOnly = {
    DBFunctionWithPositionedParamsOnly(functionName)
  }

  sealed case class DBFunctionWithPositionedParamsOnly private(functionName: String,
                                                               params: ParamsMap = ListMap.empty
                                                              ) extends DBFunction(functionName, params) {
    def setParam[T: AllowedParamTypes](value: T): DBFunctionWithPositionedParamsOnly = {
      val key = Left(params.size + 1)
      val fnc = SetterFnc.createSetterFnc(value)
      DBFunctionWithPositionedParamsOnly(functionName, params + (key, fnc))
    }

    def setParamNull(): DBFunctionWithPositionedParamsOnly = {
      val key = Left(params.size + 1)
      val fnc = SetterFnc.nullSetterFnc
      DBFunctionWithPositionedParamsOnly(functionName, params + (key, fnc))
    }

  }

  sealed case class DBFunctionWithNamedParamsToo private(functionName: String,
                                                         params: ParamsMap = ListMap.empty
                                                        ) extends DBFunction(functionName, params)
}
