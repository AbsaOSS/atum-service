package za.co.absa.atum.web.api.repositories

import java.sql.{Connection, DriverManager}

object DatabaseConnection {
  def getConnection(): Connection = {
    DriverManager.getConnection("connectionString", "user", "password")
  }

}
