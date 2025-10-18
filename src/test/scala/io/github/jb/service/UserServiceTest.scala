package io.github.jb.service

import cats.MonadError
import cats.effect.IO
import cats.effect.Resource
import cats.syntax.all.*
import munit.CatsEffectSuite
import com.dimafeng.testcontainers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import doobie.util.ExecutionContexts
import doobie.hikari.HikariTransactor
import doobie.implicits.{toConnectionIOOps, toSqlInterpolator}
import io.github.jb.CustomMonadError
import org.flywaydb.core.Flyway
import io.github.jb.domain.*
import io.github.jb.config.*
import io.github.jb.repository.DoobieUserRepository

import java.util.UUID
import scala.concurrent.duration.DurationInt

class UserServiceTest extends CatsEffectSuite {

  lazy val dbContainer: PostgreSQLContainer = {
    val pg = new PostgreSQLContainer(Some(DockerImageName.parse("postgres:17")))
    pg.start()
    pg
  }

  private lazy val transactorResource: Resource[IO, HikariTransactor[IO]] =
    HikariTransactor.newHikariTransactor[IO](
      "org.postgresql.Driver",
      dbContainer.jdbcUrl,
      dbContainer.username,
      dbContainer.password,
      ExecutionContexts.synchronous
    )

  private lazy val (transactor: HikariTransactor[IO], cleanup) = transactorResource.allocated.unsafeRunSync()

  private lazy val userRepo: DoobieUserRepository[IO] =
    new DoobieUserRepository[IO](transactor)

  private lazy val jwtService: JwtServiceImpl[IO] =
    new JwtServiceImpl[IO](
      JwtConfig("test-secret-key-for-testing-only-very-long", "15 minutes", "30 days")
    )

  private lazy val passwordService: PasswordServiceImpl[IO] =
    new PasswordServiceImpl[IO](12)

  private lazy val userService: UserServiceImpl[IO] = {
    given MonadError[IO, ApiError] = new CustomMonadError
    new UserServiceImpl[IO](userRepo, jwtService, passwordService)
  }

  private lazy val testServices: (DoobieUserRepository[IO], UserServiceImpl[IO]) = (userRepo, userService)

  override def beforeAll(): Unit = {
    super.beforeAll()
    // execute migrations
    val flyway = Flyway
      .configure()
      .dataSource(dbContainer.jdbcUrl, dbContainer.username, dbContainer.password)
      .locations("classpath:db/migration")
      .load()
    flyway.migrate()
  }

  override def afterAll(): Unit = {
    // Clean up transactor
    cleanup.unsafeRunSync()
    // Stop container
    dbContainer.stop()
    super.afterAll()
  }

  private def withServices[A](
      test: (DoobieUserRepository[IO], UserServiceImpl[IO]) => IO[A]
  ): IO[A] =
    IO(testServices)
      .flatMap { services =>
        import doobie.*
        // before each test clean database
        sql"DELETE from users".update.run.transact(transactor).void.map(_ => services)
      }
      .flatMap { case (userRepo, userService) => test(userRepo, userService) }

  test("create user successfully") {

    withServices { case (userRepo, userService) =>
      val userCreate = UserCreate(
        email = "newuser@example.com",
        username = "newuser",
        password = "securePassword123",
        firstName = Some("New"),
        lastName = Some("User")
      )

      for {
        userResponse <- userService.createUser(userCreate)
        foundUser <- userRepo.findByEmail("newuser@example.com")
      } yield {
        assertEquals(userResponse.email, "newuser@example.com")
        assertEquals(userResponse.username, "newuser")
        assertEquals(userResponse.firstName, Some("New"))
        assert(foundUser.isDefined)
        assertEquals(foundUser.map(_.email), Some("newuser@example.com"))
      }
    }
  }

  test("fail to create user with duplicate email") {
    withServices { case (_, userService) =>
      val userCreate = UserCreate(
        email = "duplicate@example.com",
        username = "user1",
        password = "password",
        firstName = None,
        lastName = None
      )

      for {
        _ <- userService.createUser(userCreate)
        result <- userService.createUser(userCreate.copy(username = "user2")).attempt
      } yield {
        assert(result.isLeft)
        assert(result.left.get.getMessage.contains("already exists"))
      }
    }
  }

  test("fail to create user with duplicate username") {
    withServices { case (_, userService) =>
      val userCreate1 = UserCreate(
        email = "user1@example.com",
        username = "sameusername",
        password = "password",
        firstName = None,
        lastName = None
      )
      val userCreate2 = UserCreate(
        email = "user2@example.com",
        username = "sameusername",
        password = "password",
        firstName = None,
        lastName = None
      )

      for {
        _ <- userService.createUser(userCreate1)
        result <- userService.createUser(userCreate2).attempt
      } yield {
        assert(result.isLeft)
      }
    }
  }

  test("login successfully with correct credentials") {
    withServices { case (_, userService) =>
      val userCreate = UserCreate(
        email = "login@example.com",
        username = "loginuser",
        password = "correctPassword",
        firstName = None,
        lastName = None
      )

      for {
        _ <- userService.createUser(userCreate)
        authResponse <- userService.login(LoginRequest("login@example.com", "correctPassword"))
      } yield {
        assertEquals(authResponse.user.email, "login@example.com")
        assertEquals(authResponse.user.username, "loginuser")
        assert(authResponse.tokens.accessToken.nonEmpty)
        assert(authResponse.tokens.refreshToken.nonEmpty)
        assertEquals(authResponse.tokens.tokenType, "Bearer")
      }
    }
  }

  test("fail login with incorrect password") {
    withServices { case (_, userService) =>
      val userCreate = UserCreate(
        email = "login2@example.com",
        username = "loginuser2",
        password = "correctPassword",
        firstName = None,
        lastName = None
      )

      for {
        _ <- userService.createUser(userCreate)
        result <- userService.login(LoginRequest("login2@example.com", "wrongPassword")).attempt
      } yield {
        assert(result.isLeft)
        assert(result.left.get.getMessage.contains("Invalid credentials"))
      }
    }
  }

  test("fail login with non-existent email") {
    withServices { case (_, userService) =>
      for {
        result <- userService.login(LoginRequest("nonexistent@example.com", "password")).attempt
      } yield {
        assert(result.isLeft)
        assert(result.left.get.getMessage.contains("Invalid credentials"))
      }
    }
  }

  test("refresh tokens successfully") {
    withServices { case (_, userService) =>
      val userCreate = UserCreate(
        email = "refresh@example.com",
        username = "refreshuser",
        password = "password",
        firstName = None,
        lastName = None
      )

      for {
        _ <- userService.createUser(userCreate)
        loginResponse <- userService.login(LoginRequest("refresh@example.com", "password"))
        _ <- IO.sleep(1.seconds) // need some time
        refreshResponse <- userService.refreshTokens(loginResponse.tokens.refreshToken)
      } yield {
        assertEquals(refreshResponse.user.email, "refresh@example.com")
        assert(refreshResponse.tokens.accessToken.nonEmpty)
        assert(refreshResponse.tokens.refreshToken.nonEmpty)
        assert(loginResponse.tokens.accessToken != refreshResponse.tokens.accessToken)
        assert(loginResponse.tokens.refreshToken != refreshResponse.tokens.refreshToken)
      }
    }
  }

  test("fail to refresh tokens with invalid refresh token") {
    withServices { case (_, userService) =>
      for {
        result <- userService.refreshTokens("invalid.refresh.token").attempt
      } yield {
        assert(result.isLeft)
        assert(result.left.get.getMessage.contains("Invalid refresh token"))
      }
    }
  }

  test("fail to refresh tokens with malformed JWT") {
    withServices { case (_, userService) =>
      for {
        result <- userService.refreshTokens("header.payload.signature").attempt
      } yield {
        assert(result.isLeft)
      }
    }
  }

  test("get user by id successfully") {
    withServices { case (userRepo, userService) =>
      val userCreate = UserCreate(
        email = "getuser@example.com",
        username = "getuser",
        password = "password",
        firstName = Some("Get"),
        lastName = Some("User")
      )

      for {
        createdUser <- userService.createUser(userCreate)
        retrievedUser <- userService.getUser(createdUser.id)
      } yield {
        assertEquals(retrievedUser.id, createdUser.id)
        assertEquals(retrievedUser.email, "getuser@example.com")
        assertEquals(retrievedUser.username, "getuser")
        assertEquals(retrievedUser.firstName, Some("Get"))
      }
    }
  }

  test("fail to get non-existent user") {
    withServices { case (_, userService) =>
      val nonExistentId = UUID.randomUUID()

      for {
        result <- userService.getUser(nonExistentId).attempt
      } yield {
        assert(result.isLeft)
        assert(result.left.get.getMessage.contains("User not found"))
      }
    }
  }

  test("update user status successfully") {
    withServices { case (userRepo, userService) =>
      val userCreate = UserCreate(
        email = "status@example.com",
        username = "statususer",
        password = "password",
        firstName = None,
        lastName = None
      )

      for {
        createdUser <- userService.createUser(userCreate)
        initialUser <- userRepo.findById(createdUser.id)
        updateResult <- userService.updateUserStatus(createdUser.id, isActive = false)
        updatedUser <- userRepo.findById(createdUser.id)
      } yield {
        assertEquals(initialUser.map(_.isActive), Some(true))
        assert(updateResult)
        assertEquals(updatedUser.map(_.isActive), Some(false))
      }
    }
  }

  test("list active users successfully") {
    withServices { case (_, userService) =>
      val users = List(
        UserCreate("active1@example.com", "active1", "pass", None, None),
        UserCreate("active2@example.com", "active2", "pass", None, None),
        UserCreate("inactive@example.com", "inactive", "pass", None, None)
      )

      for {
        createdUsers <- users.traverse(userService.createUser)
        _ <- userService.updateUserStatus(createdUsers(2).id, isActive = false)
        activeUsers <- userService.listActiveUsers(0L, 10L)
      } yield {
        assertEquals(activeUsers.size, 2)
        assert(activeUsers.forall(_.isActive))
      }
    }
  }

  test("validate user access with valid token") {
    withServices { case (_, userService) =>
      val userCreate = UserCreate(
        email = "validate@example.com",
        username = "validateuser",
        password = "password",
        firstName = None,
        lastName = None
      )

      for {
        _ <- userService.createUser(userCreate)
        loginResponse <- userService.login(LoginRequest("validate@example.com", "password"))
        user <- userService.validateUserForAccess(loginResponse.tokens.accessToken)
      } yield {
        assertEquals(user.email, "validate@example.com")
        assertEquals(user.username, "validateuser")
        assert(user.isActive)
      }
    }
  }

  test("fail to validate user access with invalid token") {
    withServices { case (_, userService) =>
      for {
        result <- userService.validateUserForAccess("invalid.token.here").attempt
      } yield {
        assert(result.isLeft)
        result match {
          case Left(value) =>
            assert(value.isInstanceOf[ApiError.InvalidOrExpiredToken.type])
          case Right(value) =>
            fail("Should have failed")
        }
      }
    }
  }

  test("fail to validate user access when user is inactive") {
    withServices { case (_, userService) =>
      val userCreate = UserCreate(
        email = "inactive@example.com",
        username = "inactiveuser",
        password = "password",
        firstName = None,
        lastName = None
      )

      for {
        createdUser <- userService.createUser(userCreate)
        _ <- userService.updateUserStatus(createdUser.id, isActive = false)
        result <- userService.login(LoginRequest("inactive@example.com", "password")).attempt
      } yield {
        assert(result.isLeft)
        result match {
          case Left(value) =>
            assert(value.isInstanceOf[ApiError.AccountDeactivated.type])
          case Right(value) =>
            fail("Should have failed")
        }
      }
    }
  }
}
