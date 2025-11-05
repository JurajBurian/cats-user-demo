package io.github.jb.service

import cats.{Applicative, MonadError}
import cats.effect.IO
import cats.effect.Resource
import cats.mtl.Handle
import cats.syntax.all.*
import munit.CatsEffectSuite
import com.dimafeng.testcontainers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import doobie.util.ExecutionContexts
import doobie.hikari.HikariTransactor
import doobie.implicits.{toConnectionIOOps, toSqlInterpolator}
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
  ): IO[A] = {
    IO(testServices)
      .flatMap { services =>
        import doobie.*
        // before each test clean database
        sql"DELETE from users".update.run.transact(transactor).void.map(_ => services)
      }
      .flatMap { case (userRepo, userService) =>
        test(userRepo, userService)
      }
  }

  private def onResult[E, R](
      p: Either[E, R],
      right: (R) => Unit
  ): Unit = p match {
    case Right(r) => right(r)
    case Left(e)  => fail(s"Failed with: $e")
  }

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
        userResponse <- userService.createUser(userCreate).value
        foundUser <- userRepo.findByEmail("newuser@example.com").value
      } yield {
        onResult(
          userResponse,
          { userResponse =>
            assertEquals(userResponse.email, "newuser@example.com")
            assertEquals(userResponse.username, "newuser")
            assertEquals(userResponse.firstName, Some("New"))
          }
        )
        onResult(
          foundUser,
          { foundUser =>
            assertEquals(foundUser.map(_.email), Some("newuser@example.com"))
            assert(foundUser.isDefined)
          }
        )
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
        _ <- userService.createUser(userCreate).value
        result <- userService.createUser(userCreate.copy(username = "user2")).value
      } yield {
        result match {
          case Left(value: UserAlreadyExistsErr) =>
            assertEquals(value.email, "duplicate@example.com")
          case Left(x) =>
            fail(s"Failed with wrong errorr: $x")
          case _ => fail("Should have failed")
        }
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
        _ <- userService.createUser(userCreate).value
        authResponse <- userService.login(LoginRequest("login@example.com", "correctPassword")).value
      } yield {
        onResult(
          authResponse,
          { authResponse =>
            assertEquals(authResponse.user.email, "login@example.com")
            assertEquals(authResponse.user.username, "loginuser")
            assert(authResponse.tokens.accessToken.nonEmpty)
            assert(authResponse.tokens.refreshToken.nonEmpty)
            assertEquals(authResponse.tokens.tokenType, "Bearer")
          }
        )
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
        _ <- userService.createUser(userCreate).value
        result <- userService.login(LoginRequest("login2@example.com", "wrongPassword")).value
      } yield {
        assert(result.isLeft)
        result match {
          case Left(value) =>
            assert(value.isInstanceOf[InvalidCredentials])
          case _ => fail("Should have failed")

        }
      }
    }

    test("fail login with non-existent email") {
      withServices { case (_, userService) =>
        for {
          result <- userService.login(LoginRequest("nonexistent@example.com", "password")).value
        } yield {
          result match {
            case Left(value) =>
              assert(value.isInstanceOf[InvalidCredentials])
            case _ => fail("Should have failed")
          }
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
          _ <- userService.createUser(userCreate).value
          loginResponse <- userService.login(LoginRequest("refresh@example.com", "password")).value
          _ <- IO.sleep(1.seconds) // need some time
          refreshResponse <- loginResponse match {
            case Left(value) =>
              fail("Should have failed")
            case Right(value) =>
              userService.refreshTokens(value.tokens.refreshToken).value
          }
        } yield {
          onResult(
            refreshResponse,
            { refreshResponse =>
              assertEquals(refreshResponse.user.email, "refresh@example.com")
              assert(refreshResponse.tokens.accessToken.nonEmpty)
              assert(refreshResponse.tokens.refreshToken.nonEmpty)
            }
          )
          onResult(
            loginResponse,
            { loginResponse =>
              assert(loginResponse.tokens.accessToken.nonEmpty)
              assert(loginResponse.tokens.refreshToken.nonEmpty)
            }
          )
        }
      }
    }

    test("fail to refresh tokens with invalid refresh token") {
      withServices { case (_, userService) =>
        for {
          result <- userService.refreshTokens("invalid.refresh.token").value
        } yield {
          result match {
            case Left(value) =>
              assert(value.isInstanceOf[InvalidOrExpiredRefreshToken])
            case Right(value) =>
              fail("Should have failed")
          }
        }
      }
    }

    test("fail to refresh tokens with malformed JWT") {
      withServices { case (_, userService) =>
        for {
          result <- userService.refreshTokens("kokiLoki").value
        } yield {
          result match {
            case Left(value) =>
              assert(value.isInstanceOf[InvalidOrExpiredRefreshToken])
            case Right(value) =>
              fail("Should have failed")
          }
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
          createdUser <- userService.createUser(userCreate).value
          retrievedUser <- createdUser match {
            case Left(value) =>
              fail("Should have failed")
            case Right(value) =>
              userService.getUser(value.id).value
          }
        } yield {

          val cu = createdUser match {
            case Left(value) =>
              fail("Should have failed")
            case Right(value) => value
          }
          val ru = retrievedUser match {
            case Left(value) =>
              fail("Should have failed")
            case Right(value) => value
          }
          assertEquals(ru.id, cu.id)
          assertEquals(ru.email, "getuser@example.com")
          assertEquals(ru.username, "getuser")
          assertEquals(ru.firstName, Some("Get"))
        }
      }
    }

    test("fail to get non-existent user") {
      withServices { case (_, userService) =>
        val nonExistentId = UUID.randomUUID()

        for {
          result <- userService.getUser(nonExistentId).value
        } yield {
          result match {
            case Left(value) =>
              assert(value.isInstanceOf[UserNotFound])
            case Right(value) =>
              fail("Should have failed")
          }
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
          createdUser <- userService.createUser(userCreate).value
          initialUser <- createdUser match {
            case Left(value) =>
              fail("Should have failed")
            case Right(v) =>
              userRepo.findById(v.id).value
          }
          updateResult <- initialUser match {
            case Left(value) =>
              fail("Should have failed")
            case Right(v) =>
              userService.updateUserStatus(v.get.id, isActive = false).value
          }
          updatedUser <- createdUser match {
            case Left(value) =>
              fail("Should have failed")
            case Right(v) =>
              userRepo.findById(v.id).value
          }
        } yield {
          onResult(
            updateResult,
            { updateResult =>
              assertEquals(updateResult, true)
            }
          )
          onResult(
            initialUser,
            { initialUser =>
              assertEquals(initialUser.map(_.isActive), Some(true))
            }
          )
          onResult(
            updatedUser,
            { updatedUser =>
              assertEquals(updatedUser.map(_.isActive), Some(false))
            }
          )
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
          createdUsers <- users.traverse(userService.createUser).value
          _ <- createdUsers match {
            case Left(value) =>
              fail("Should have failed")
            case Right(value) =>
              userService.updateUserStatus(value.get(2).get.id, isActive = false).value
          }
          activeUsers <- userService.listActiveUsers(0L, 10L).value
        } yield {
          onResult(
            activeUsers,
            { activeUsers =>
              assertEquals(activeUsers.size, 2)
              assert(activeUsers.forall(_.isActive))
            }
          )
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
          _ <- userService.createUser(userCreate).value
          loginResponse <- userService.login(LoginRequest("validate@example.com", "password")).value
          user <- loginResponse match {
            case Left(value) =>
              fail("Should have failed")
            case Right(value) =>
              userService.validateUserForAccess(value.tokens.accessToken).value
          }
        } yield {
          onResult(
            user,
            { user =>
              assertEquals(user.email, "validate@example.com")
              assertEquals(user.username, "validateuser")
              assert(user.isActive)
            }
          )
        }
      }
    }

    test("fail to validate user access with invalid token") {
      withServices { case (_, userService) =>
        for {
          result <- userService.validateUserForAccess("invalid.token.here").value
        } yield {
          assert(result.isLeft)
          result match {
            case Left(value) =>
              assert(value.isInstanceOf[InvalidOrExpiredToken])
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
          createdUser <- userService.createUser(userCreate).value
          _ <- createdUser match {
            case Left(value) =>
              fail("Should have failed")
            case Right(value) =>
              userService.updateUserStatus(value.id, isActive = false).value
          }
          result <- userService.login(LoginRequest("inactive@example.com", "password")).value
        } yield {
          assert(result.isLeft)
          result match {
            case Left(value) =>
              assert(value.isInstanceOf[AccountDeactivated])
            case Right(value) =>
              fail("Should have failed")
          }
        }
      }
    }
  }
}
