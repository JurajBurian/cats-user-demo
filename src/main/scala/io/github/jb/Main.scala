package io.github.jb

import io.github.jb.config.*
import io.github.jb.domain.*
import io.github.jb.service.*
import cats.effect.{IO, IOApp, Resource}
import cats.mtl.Handle
import com.zaxxer.hikari.HikariConfig
import doobie.hikari.HikariTransactor
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import io.github.jb.repository.DoobieUserRepository
import io.github.jb.http.Endpoints
import org.flywaydb.core.Flyway
import sttp.tapir.server.netty.cats.NettyCatsServer

import scala.concurrent.ExecutionContext

object Main extends IOApp.Simple {

  given LoggerFactory[IO] = Slf4jFactory.create[IO]

  // Get the logger instance
  private val logger = org.typelevel.log4cats.slf4j.Slf4jLogger.getLogger[IO]

  private def transactor(config: DatabaseConfig): Resource[IO, HikariTransactor[IO]] = {
    val connectEC = ExecutionContext.global

    val hikariConfig = {
      val cfg = new HikariConfig()
      cfg.setDriverClassName(config.driver)
      cfg.setJdbcUrl(config.url)
      cfg.setUsername(config.user)
      cfg.setPassword(config.password)
      cfg.setConnectionTimeout(2000)
      cfg.setInitializationFailTimeout(2000)
      cfg.setValidationTimeout(2000)
      cfg.setConnectionTestQuery("SELECT 1")
      cfg
    }

    HikariTransactor.fromHikariConfig[IO](hikariConfig)
  }

  private def initialize(transactor: HikariTransactor[IO]): IO[Unit] = transactor.configure { dataSource =>
    IO {
      val flyway = Flyway.configure().dataSource(dataSource).schemas("public").load()
      flyway.migrate()
      ()
    }
  }

  given Handle[IO, ApiError] = new CustomHandle

  private def createUserService(xa: HikariTransactor[IO], jwt: JwtConfig, bcrypt: BcryptConfig) = {

    val userRepo = new DoobieUserRepository[IO](xa)
    val jwtService = new JwtServiceImpl[IO](jwt)
    val passwordService = new PasswordServiceImpl[IO](bcrypt.cost)
    new UserServiceImpl[IO](userRepo, jwtService, passwordService)
  }

  override def run: IO[Unit] = {

    val serverResource = for {
      config <- Resource.eval(Config.load)
      _ <- Resource.eval(logger.info("Starting User Management API..."))
      xa <- transactor(config.database)
      _ <- Resource.eval(initialize(xa))

      // Create endpoints
      endpoints = new Endpoints[IO](createUserService(xa, config.jwt, config.bcrypt))

      // Create OpenAPI documentation
      openApiEndpoints = endpoints.allEndpoints.map(_.endpoint)
      docEndpoints = sttp.tapir.swagger.bundle
        .SwaggerInterpreter()
        .fromEndpoints[IO](openApiEndpoints, "User Management API", "1.0")

      // Create Netty server resource
      server <- NettyCatsServer
        .io()
        .flatMap { server =>
          Resource.make(
            server
              .host(config.http.host)
              .port(config.http.port)
              .addEndpoints(endpoints.allEndpoints ++ docEndpoints)
              .start()
          )(_.stop())
        }
    } yield (server, config) // Return both server and config

    serverResource
      .use { case (server, config) =>
        logger.info(s"Server started at http://${config.http.host}:${config.http.port}") *>
          logger.info(s"API documentation available at http://${config.http.host}:${config.http.port}/docs") *>
          IO.never
      }
  }
}
