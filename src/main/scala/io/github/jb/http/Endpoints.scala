// In http/Endpoints.scala
package io.github.jb.http

import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.ServerEndpoint
import cats.Monad
import io.github.jb.*
import domain.*
import sttp.model.StatusCode

import java.util.UUID

class Endpoints[F[_]](userService: UserService[F])(using M: Monad[F]) {

  private val basePath = "api" / "v1"
  private val bearerTokenHeader = auth.bearer[String]()

  // Define specific error variants for each ApiError type
  private val invalidCredentialsError =
    oneOfVariant(
      StatusCode.Unauthorized,
      jsonBody[InvalidCredentials]
        .description("Invalid email or password credentials provided")
        .example(InvalidCredentials())
    )
  private val invalidRefreshTokenError =
    oneOfVariant(
      StatusCode.Unauthorized,
      jsonBody[InvalidOrExpiredRefreshToken]
        .description("Refresh token is invalid, expired, or malformed")
        .example(InvalidOrExpiredRefreshToken())
    )
  private val invalidOrExpiredTokenError =
    oneOfVariant(
      StatusCode.Unauthorized,
      jsonBody[InvalidOrExpiredToken]
        .description("Access token is invalid, expired, or malformed")
        .example(InvalidOrExpiredToken())
    )
  private val accountDeactivatedError =
    oneOfVariant(
      StatusCode.Forbidden,
      jsonBody[AccountDeactivated]
        .description("User account has been deactivated and cannot access the system")
        .example(AccountDeactivated())
    )
  private val userNotFoundError =
    oneOfVariant(
      StatusCode.NotFound,
      jsonBody[UserNotFound]
        .description("User with the specified ID was not found")
        .example(UserNotFound(UUID.randomUUID()))
    )
  private val userAlreadyExistsError =
    oneOfVariant(
      StatusCode.Conflict,
      jsonBody[UserAlreadyExists]
        .description("User with this email address already exists in the system")
        .example(UserAlreadyExists("janko.hrasko@hraskovo.com"))
    )
  private val validationError = {
    val allValidationReasons = ValidationErrorReason.values.map(_.toString).mkString(", ")
    oneOfVariant(
      StatusCode.BadRequest,
      jsonBody[io.github.jb.domain.ValidationError]
        .description(s"Input validation failed. Possible reasons: $allValidationReasons")
        .example(domain.ValidationError(List(ValidationErrorReason.InvalidEmailFormat)))
    )
  }
  private val internalServerError =
    oneOfVariant(
      StatusCode.InternalServerError,
      jsonBody[InternalServerError]
        .example(
          InternalServerError(
            "HikariPool-1 - Connection is not available, request timed out after 2000ms (total=0, active=0, idle=0, waiting=0"
          )
        )
        .map(body => InternalServerErrorWithTh(body, new RuntimeException(body.cause)))(e => e.body)
        .description("Unexpected internal server error occurred")
    )

  // Each endpoint specifies exactly which errors it can return
  val loginEndpoint: ServerEndpoint[Any, F] =
    endpoint.post
      .in(basePath / "auth" / "login")
      .in(jsonBody[LoginRequest])
      .out(jsonBody[AuthResponse])
      .errorOut(
        oneOf(
          invalidCredentialsError,
          accountDeactivatedError,
          internalServerError
        )
      )
      .description("Authenticate user with email and password, return JWT tokens")
      .serverLogic(loginRequest => userService.login(loginRequest).value)

  val refreshEndpoint: ServerEndpoint[Any, F] =
    endpoint.post
      .in(basePath / "auth" / "refresh")
      .in(bearerTokenHeader)
      .out(jsonBody[AuthResponse])
      .errorOut(
        oneOf(
          invalidRefreshTokenError,
          userNotFoundError,
          accountDeactivatedError,
          internalServerError
        )
      )
      .description("Generate new access token using valid refresh token")
      .serverLogic(refreshToken => userService.refreshTokens(refreshToken).value)

  val createUserEndpoint: ServerEndpoint[Any, F] =
    endpoint.post
      .in(basePath / "users")
      .in(jsonBody[UserCreate])
      .out(jsonBody[UserResponse])
      .errorOut(
        oneOf(
          validationError,
          userAlreadyExistsError,
          internalServerError
        )
      )
      .description("Create new user account with email, username, and password")
      .serverLogic(userCreate => userService.createUser(userCreate).value)

  val getUserEndpoint: ServerEndpoint[Any, F] =
    endpoint.get
      .in(basePath / "users" / path[UUID]("id"))
      .in(bearerTokenHeader)
      .out(jsonBody[UserResponse])
      .errorOut(
        oneOf(
          invalidOrExpiredTokenError,
          userNotFoundError,
          accountDeactivatedError,
          internalServerError
        )
      )
      .description("Retrieve user profile information by user ID")
      .serverLogic { case (id, accessToken) =>
        (for {
          _ <- userService.validateUserForAccess(accessToken)
          user <- userService.getUser(id)
        } yield user).value
      }

  val updateUserStatusEndpoint: ServerEndpoint[Any, F] =
    endpoint.patch
      .in(basePath / "users" / path[UUID]("id") / "status")
      .in(bearerTokenHeader)
      .in(jsonBody[UserStatusUpdate])
      .out(jsonBody[Boolean])
      .errorOut(
        oneOf(
          invalidOrExpiredTokenError,
          userNotFoundError,
          internalServerError,
          accountDeactivatedError
        )
      )
      .description("Activate or deactivate user account status")
      .serverLogic { case (id, accessToken, statusUpdate) =>
        (for {
          _ <- userService.validateUserForAccess(accessToken)
          result <- userService.updateUserStatus(id, statusUpdate.isActive)
        } yield result).value

      }

  val listUsersEndpoint: ServerEndpoint[Any, F] =
    endpoint.get
      .in(basePath / "users" / path[Long]("offset") / path[Long]("count") / "list")
      .in(bearerTokenHeader)
      .out(jsonBody[List[UserResponse]])
      .errorOut(
        oneOf(
          invalidOrExpiredTokenError,
          internalServerError,
          accountDeactivatedError,
          userNotFoundError
        )
      )
      .description("List active users with pagination support")
      .serverLogic { case (offset, count, accessToken) =>
        (for {
          _ <- userService.validateUserForAccess(accessToken)
          users <- userService.listActiveUsers(offset, count)
        } yield users).value
      }

  val allEndpoints: List[ServerEndpoint[Any, F]] =
    List(
      loginEndpoint,
      refreshEndpoint,
      createUserEndpoint,
      getUserEndpoint,
      updateUserStatusEndpoint,
      listUsersEndpoint
    )
}
