package io.github.jb.service

import cats.effect.IO
import cats.effect.Resource
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

  private lazy val passwordServiceInst: PasswordService[IO] =
    new PasswordServiceImpl[IO](12)

  private lazy val userServiceInst: UserServiceImpl[IO] = {
    new UserServiceImpl[IO](userRepo, jwtService, passwordServiceInst)
  }

  private def passwordService[F[_]] = passwordServiceInst

  private def userService[F[_]] = userServiceInst

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

  override def beforeEach(context: BeforeEach): Unit = {
    super.beforeEach(context)
    import doobie.*
    sql"DELETE from users".update.run.transact(transactor).unsafeRunSync()
  }

  override def afterAll(): Unit = {
    // Clean up transactor
    cleanup.unsafeRunSync()
    // Stop container
    dbContainer.stop()
    super.afterAll()
  }

  test("create user successfully") {
    val userCreate = UserCreate(
      email = "newuser@example.com",
      username = "newuser",
      password = "securePassword123",
      firstName = Some("New"),
      lastName = Some("User")
    )

    (for {
      userResponse <- userService.createUser(userCreate)
      foundUser <- userRepo.findByEmail("newuser@example.com")
    } yield (userResponse, foundUser)).value.map {
      case Right((userResponse, foundUser)) =>
        assertEquals(userResponse.email, "newuser@example.com")
        assertEquals(userResponse.username, "newuser")
        assertEquals(userResponse.firstName, Some("New"))
        assertEquals(foundUser.map(_.email), Some("newuser@example.com"))
        assert(foundUser.isDefined)
      case Left(e) => fail(s"Failed with: $e")
    }
  }

  test("fail to create user with duplicate email") {
    val userCreate = UserCreate(
      email = "duplicate@example.com",
      username = "user1",
      password = "password",
      firstName = None,
      lastName = None
    )

    (for {
      _ <- userService.createUser(userCreate)
      result <- userService.createUser(userCreate.copy(username = "user2"))
    } yield result).value.map {
      case Left(value: UserAlreadyExists) =>
        assertEquals(value.email, "duplicate@example.com")
      case Left(x) =>
        fail(s"Failed with wrong error: $x")
      case _ => fail("Should have failed")
    }
  }

  test("login successfully with correct credentials") {
    val userCreate = UserCreate(
      email = "login@example.com",
      username = "loginuser",
      password = "correctPassword",
      firstName = None,
      lastName = None
    )

    // Use flatMap instead of for-comprehension to preserve types
    userService.createUser(userCreate).value.flatMap {
      case Right(_) =>
        userService.login(LoginRequest("login@example.com", "correctPassword")).value.map {
          case Right(authResponse) =>
            assertEquals(authResponse.user.email, "login@example.com")
            assertEquals(authResponse.user.username, "loginuser")
            assert(authResponse.tokens.accessToken.nonEmpty)
            assert(authResponse.tokens.refreshToken.nonEmpty)
            assertEquals(authResponse.tokens.tokenType, "Bearer")
          case Left(e) => fail(s"Login failed with: $e")
        }
      case Left(e) => IO(fail(s"User creation failed with: $e"))
    }
  }

  test("fail login with incorrect password") {
    val userCreate = UserCreate(
      email = "login2@example.com",
      username = "loginuser2",
      password = "correctPassword",
      firstName = None,
      lastName = None
    )

    userService.createUser(userCreate).value.flatMap {
      case Right(_) =>
        userService.login(LoginRequest("login2@example.com", "wrongPassword")).value.map {
          case Left(value) =>
            assert(value.isInstanceOf[InvalidCredentials])
          case Right(_) =>
            fail("Should have failed")
        }
      case Left(e) => IO(fail(s"User creation failed with: $e"))
    }
  }

  test("fail login with non-existent email") {
    userService.login(LoginRequest("nonexistent@example.com", "password")).value.map {
      case Left(value) =>
        assert(value.isInstanceOf[InvalidCredentials])
      case Right(_) =>
        fail("Should have failed")
    }
  }

  test("refresh tokens successfully") {
    val userCreate = UserCreate(
      email = "refresh@example.com",
      username = "refreshuser",
      password = "password",
      firstName = None,
      lastName = None
    )

    userService.createUser(userCreate).value.flatMap {
      case Right(_) =>
        userService.login(LoginRequest("refresh@example.com", "password")).value.flatMap {
          case Right(loginResponse) =>
            IO.sleep(1.second) *>
              userService.refreshTokens(loginResponse.tokens.refreshToken).value.map {
                case Right(refreshResponse) =>
                  assertEquals(refreshResponse.user.email, "refresh@example.com")
                  assert(refreshResponse.tokens.accessToken.nonEmpty)
                  assert(refreshResponse.tokens.refreshToken.nonEmpty)
                  assert(loginResponse.tokens.accessToken.nonEmpty)
                  assert(loginResponse.tokens.refreshToken.nonEmpty)
                case Left(e) => fail(s"Refresh failed with: $e")
              }
          case Left(e) => IO(fail(s"Login failed with: $e"))
        }
      case Left(e) => IO(fail(s"User creation failed with: $e"))
    }
  }

  test("fail to refresh tokens with invalid refresh token") {
    userService.refreshTokens("invalid.refresh.token").value.map {
      case Left(value) =>
        assert(value.isInstanceOf[InvalidOrExpiredRefreshToken])
      case Right(_) =>
        fail("Should have failed")
    }
  }

  test("fail to refresh tokens with malformed JWT") {
    userService.refreshTokens("kokiLoki").value.map {
      case Left(value) =>
        assert(value.isInstanceOf[InvalidOrExpiredRefreshToken])
      case Right(_) =>
        fail("Should have failed")
    }
  }

  test("get user by id successfully") {
    val userCreate = UserCreate(
      email = "getuser@example.com",
      username = "getuser",
      password = "password",
      firstName = Some("Get"),
      lastName = Some("User")
    )

    userService.createUser(userCreate).value.flatMap {
      case Right(createdUser) =>
        userService.getUser(createdUser.id).value.map {
          case Right(retrievedUser) =>
            assertEquals(retrievedUser.id, createdUser.id)
            assertEquals(retrievedUser.email, "getuser@example.com")
            assertEquals(retrievedUser.username, "getuser")
            assertEquals(retrievedUser.firstName, Some("Get"))
          case Left(e) => fail(s"Get user failed with: $e")
        }
      case Left(e) => IO(fail(s"User creation failed with: $e"))
    }
  }

  test("fail to get non-existent user") {
    val nonExistentId = UUID.randomUUID()

    userService.getUser(nonExistentId).value.map {
      case Left(value) =>
        assert(value.isInstanceOf[UserNotFound])
      case Right(_) =>
        fail("Should have failed")
    }
  }

  test("update user status successfully") {
    val userCreate = UserCreate(
      email = "status@example.com",
      username = "statususer",
      password = "password",
      firstName = None,
      lastName = None
    )

    userService.createUser(userCreate).value.flatMap {
      case Right(createdUser) =>
        userRepo.findById(createdUser.id).value.flatMap {
          case Right(Some(initialUser)) =>
            userService.updateUserStatus(createdUser.id, isActive = false).value.flatMap {
              case Right(updateResult) =>
                userRepo.findById(createdUser.id).value.map {
                  case Right(Some(updatedUser)) =>
                    assertEquals(updateResult, true)
                    assertEquals(initialUser.isActive, true)
                    assertEquals(updatedUser.isActive, false)
                  case _ => fail("Updated user not found")
                }
              case Left(e) => IO(fail(s"Update failed with: $e"))
            }
          case _ => IO(fail("Initial user not found"))
        }
      case Left(e) => IO(fail(s"User creation failed with: $e"))
    }
  }

  test("list active users successfully") {
    val users = List(
      UserCreate("active1@example.com", "active1", "pass", None, None),
      UserCreate("active2@example.com", "active2", "pass", None, None),
      UserCreate("inactive@example.com", "inactive", "pass", None, None)
    )

    // Create all users sequentially
    users.traverse(userService.createUser(_).value).flatMap { createdUsers =>
      // Check if all users were created successfully
      val createdUserResponses = createdUsers.collect { case Right(user) => user }
      if (createdUserResponses.size != 3) {
        IO(fail("Failed to create all users"))
      } else {
        // Deactivate the third user
        userService.updateUserStatus(createdUserResponses(2).id, isActive = false).value.flatMap {
          case Right(_) =>
            userService.listActiveUsers(0L, 10L).value.map {
              case Right(activeUsers) =>
                assertEquals(activeUsers.size, 2)
                assert(activeUsers.forall(_.isActive))
              case Left(e) => fail(s"List active users failed with: $e")
            }
          case Left(e) => IO(fail(s"Update user status failed with: $e"))
        }
      }
    }
  }

  test("validate user access with valid token") {
    val userCreate = UserCreate(
      email = "validate@example.com",
      username = "validateuser",
      password = "password",
      firstName = None,
      lastName = None
    )

    userService.createUser(userCreate).value.flatMap {
      case Right(_) =>
        userService.login(LoginRequest("validate@example.com", "password")).value.flatMap {
          case Right(loginResponse) =>
            userService.validateUserForAccess(loginResponse.tokens.accessToken).value.map {
              case Right(user) =>
                assertEquals(user.email, "validate@example.com")
                assertEquals(user.username, "validateuser")
                assert(user.isActive)
              case Left(e) => fail(s"Validate user access failed with: $e")
            }
          case Left(e) => IO(fail(s"Login failed with: $e"))
        }
      case Left(e) => IO(fail(s"User creation failed with: $e"))
    }
  }

  test("fail to validate user access with invalid token") {
    userService.validateUserForAccess("invalid.token.here").value.map {
      case Left(value) =>
        assert(value.isInstanceOf[InvalidOrExpiredToken])
      case Right(_) =>
        fail("Should have failed")
    }
  }

  test("fail to validate user access when user is inactive") {
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
          fail(s"Have been created! $value")
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
