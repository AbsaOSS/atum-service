package za.co.absa.atum.server.api

import doobie.util.transactor.{Strategy, Transactor}
import za.co.absa.atum.server.config.PostgresConfig
import zio._
import zio.interop.catz._

object TestTransactorProvider {

  val layer: ZLayer[Any, Config.Error, Transactor[Task]] = ZLayer {
    for {
      postgresConfig <- ZIO.config[PostgresConfig](PostgresConfig.config)
      transactor <- ZIO.succeed(
        Transactor.fromDriverManager[Task](
          postgresConfig.dataSourceClass,
          s"jdbc:postgresql://${postgresConfig.serverName}:${postgresConfig.portNumber}/${postgresConfig.databaseName}",
          postgresConfig.user,
          postgresConfig.password
        )
      )
    } yield transactor
  }

  val layerWithRollback: ZLayer[Any, Config.Error, Transactor[Task]] = ZLayer {
    for {
      postgresConfig <- ZIO.config[PostgresConfig](PostgresConfig.config)
      transactor <- ZIO.succeed(
        Transactor.fromDriverManager[Task](
          postgresConfig.dataSourceClass,
          s"jdbc:postgresql://${postgresConfig.serverName}:${postgresConfig.portNumber}/${postgresConfig.databaseName}",
          postgresConfig.user,
          postgresConfig.password
        )
      )
      transactorWithRollback = Transactor.strategy.set(transactor, Strategy.default.copy(after = HC.rollback))
    } yield transactorWithRollback
  }

}
