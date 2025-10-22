// In http/Endpoints.scala
package io.github.jb.http

import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.jsoniter.jsonBody
import sttp.tapir.server.ServerEndpoint
import cats.Monad
import cats.syntax.all.*
import cats.mtl.Handle
import io.github.jb.domain.*
import io.github.jb.domain.given
import io.github.jb.domain.ApiError.*
import sttp.model.StatusCode

import java.util.UUID

class Endpoints[F[_]](userService: UserService[F])(using M: Monad[F], H: Handle[F, ApiError]) {

  private val basePath = "api" / "v1"
  private val bearerTokenHeader = auth.bearer[String]()

  // Simple error handling - just convert to Either
  private def handleServiceError[T](result: F[T]): F[Either[ApiError, T]] = {
    H.attempt(result)
  }

  // Define specific error variants for each ApiError type
  private val invalidCredentialsError =
    oneOfVariant(StatusCode.Unauthorized, jsonBody[InvalidCredentials])

  private val invalidRefreshTokenError =
    oneOfVariant(StatusCode.Unauthorized, jsonBody[InvalidRefreshToken])

  private val invalidOrExpiredTokenError =
    oneOfVariant(StatusCode.Unauthorized, jsonBody[InvalidOrExpiredToken])

  private val accountDeactivatedError =
    oneOfVariant(StatusCode.Forbidden, jsonBody[AccountDeactivated])

  private val userNotFoundError =
    oneOfVariant(StatusCode.NotFound, jsonBody[UserNotFound])

  private val userAlreadyExistsError =
    oneOfVariant(StatusCode.Conflict, jsonBody[UserAlreadyExists])

  private val internalServerError =
    oneOfVariant(StatusCode.InternalServerError, jsonBody[InternalServerError])

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
      .serverLogic(loginRequest => handleServiceError(userService.login(loginRequest)))

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
      .serverLogic(refreshToken => handleServiceError(userService.refreshTokens(refreshToken)))

  val createUserEndpoint: ServerEndpoint[Any, F] =
    endpoint.post
      .in(basePath / "users")
      .in(jsonBody[UserCreate])
      .out(jsonBody[UserResponse])
      .errorOut(
        oneOf(
          userAlreadyExistsError,
          internalServerError
        )
      )
      .serverLogic(userCreate => handleServiceError(userService.createUser(userCreate)))

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
      .serverLogic { case (id, accessToken) =>
        handleServiceError {
          for
            _ <- userService.validateUserForAccess(accessToken)
            user <- userService.getUser(id)
          yield user
        }
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
          internalServerError
        )
      )
      .serverLogic { case (id, accessToken, statusUpdate) =>
        handleServiceError {
          for
            _ <- userService.validateUserForAccess(accessToken)
            result <- userService.updateUserStatus(id, statusUpdate.isActive)
          yield result
        }
      }

  val listUsersEndpoint: ServerEndpoint[Any, F] =
    endpoint.get
      .in(basePath / "users" / path[Long]("offset") / path[Long]("count") / "list")
      .in(bearerTokenHeader)
      .out(jsonBody[List[UserResponse]])
      .errorOut(
        oneOf(
          invalidOrExpiredTokenError,
          internalServerError
        )
      )
      .serverLogic { case (offset, count, accessToken) =>
        handleServiceError {
          for
            _ <- userService.validateUserForAccess(accessToken)
            users <- userService.listActiveUsers(offset, count)
          yield users
        }
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
