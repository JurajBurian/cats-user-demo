// In http/Endpoints.scala
package io.github.jb.http

import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.jsoniter.jsonBody
import sttp.tapir.server.ServerEndpoint
import cats.Monad
import cats.syntax.all.*
import cats.mtl.Handle
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import io.github.jb.domain.*
import io.github.jb.domain.given
import io.github.jb.domain.ApiError
import sttp.model.StatusCode

import java.util.UUID

class Endpoints[F[_]](userService: UserService[F])(using M: Monad[F], H: Handle[F, ApiError]) {

  case class ErrorResponse(error: String, message: String)

  given JsonValueCodec[ErrorResponse] = JsonCodecMaker.make

  private val basePath = "api" / "v1"
  private val bearerTokenHeader = auth.bearer[String]()

  def fromApiError(error: ApiError): ErrorResponse =
    ErrorResponse(error = error.productPrefix, message = error.getMessage)

  private def handleServiceError[T](result: F[T]): F[Either[ErrorResponse, T]] =
    H.attempt(result).map {
      case Right(value) => Right(value)
      case Left(error)  => Left(fromApiError(error))
    }

  // Individual error variants - now using ErrorResponse
  private val unauthorizedError =
    oneOfVariant(StatusCode.Unauthorized, jsonBody[ErrorResponse].description("Authentication error"))

  private val forbiddenError =
    oneOfVariant(StatusCode.Forbidden, jsonBody[ErrorResponse].description("Access forbidden"))

  private val notFoundError =
    oneOfVariant(StatusCode.NotFound, jsonBody[ErrorResponse].description("Resource not found"))

  private val conflictError =
    oneOfVariant(StatusCode.Conflict, jsonBody[ErrorResponse].description("Resource conflict"))

  private val internalServerError =
    oneOfVariant(StatusCode.InternalServerError, jsonBody[ErrorResponse].description("Internal server error"))

  // All endpoints remain the same - they use handleServiceError which now uses Handle
  val loginEndpoint: ServerEndpoint[Any, F] =
    endpoint.post
      .in(basePath / "auth" / "login")
      .in(jsonBody[LoginRequest])
      .out(jsonBody[AuthResponse])
      .errorOut(
        oneOf(
          unauthorizedError, // InvalidCredentials
          forbiddenError, // AccountDeactivated
          internalServerError // UnknownError
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
          unauthorizedError, // InvalidRefreshToken
          notFoundError, // UserNotFound
          forbiddenError, // AccountDeactivated
          internalServerError // UnknownError
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
          conflictError, // UserAlreadyExists
          internalServerError // UnknownError
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
          unauthorizedError, // InvalidOrExpiredToken
          notFoundError, // UserNotFound
          forbiddenError, // AccountDeactivated
          internalServerError // UnknownError
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
          unauthorizedError, // InvalidOrExpiredToken
          notFoundError, // UserNotFound
          internalServerError // UnknownError
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
          unauthorizedError, // InvalidOrExpiredToken
          internalServerError // UnknownError
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
