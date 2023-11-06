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

package za.co.absa.atum.database.balta

import org.scalactic.source
import org.scalatest.{BeforeAndAfterAll, Tag}
import org.scalatest.funsuite.AnyFunSuite
import za.co.absa.atum.database.balta.classes.DBFunction.DBFunctionWithPositionedParamsOnly
import za.co.absa.atum.database.balta.classes.setter.{AllowedParamTypes, Params}
import za.co.absa.atum.database.balta.classes.setter.Params.{NamedParams, OrderedParams}
import za.co.absa.atum.database.balta.classes.{ConnectionInfo, DBConnection, DBFunction, DBTable, QueryResult}

import java.sql.DriverManager
import java.time.OffsetDateTime
import java.util.Properties

abstract class DBTestSuite extends AnyFunSuite with BeforeAndAfterAll{

  protected lazy implicit val dbConnection: DBConnection = {
    createConnection(
      connectionInfo.dbUrl,
      connectionInfo.username,
      connectionInfo.password
    )
  }

  protected lazy val connectionInfo: ConnectionInfo = readConnectionInfoFromConfig

  override protected def test(testName: String, testTags: Tag*)
                      (testFun: => Any /* Assertion */)
                      (implicit pos: source.Position): Unit = {
    val dbTestFun = {
      try {
        testFun
      }
      finally {
        if (connectionInfo.persistData) {
          dbConnection.connection.commit()
        } else {
          dbConnection.connection.rollback()
        }
      }
    }
    super.test(testName, testTags: _*)(dbTestFun)
  }

  protected def table(tableName: String): DBTable = {
    DBTable(tableName)
  }

  protected def function(functionName: String): DBFunctionWithPositionedParamsOnly = {
    DBFunction(functionName)
  }

  protected def now()(implicit connection: DBConnection): OffsetDateTime = {
    val preparedStatement = connection.connection.prepareStatement("SELECT now() AS now")
    val prep = preparedStatement.executeQuery()
    val result = new QueryResult(prep).next().getOffsetDateTime("now").get
    prep.close()
    result
  }

  protected def add[T: AllowedParamTypes](paramName: String, value: T): NamedParams = {
    Params.add(paramName, value)
  }

  protected def addNull(paramName: String): NamedParams = {
    Params.addNull(paramName)
  }

  protected def add[T: AllowedParamTypes](value: T): OrderedParams = {
    Params.add(value)
  }

  protected def addNull[T: AllowedParamTypes](): OrderedParams = {
    Params.addNull()
  }

  // private functions
  private def createConnection(url: String, username: String, password: String): DBConnection = {
    val conn = DriverManager.getConnection(url, username, password)
    conn.setAutoCommit(false)
    new DBConnection(conn)
  }

  private def readConnectionInfoFromConfig = {
    val properties = new Properties()
    properties.load(getClass.getResourceAsStream("/database.properties"))

    ConnectionInfo(
      dbUrl = properties.getProperty("test.jdbc.url"),
      username = properties.getProperty("test.jdbc.username"),
      password = properties.getProperty("test.jdbc.password"),
      persistData = properties.getProperty("test.persist.db", "false").toBoolean
    )
  }
}
