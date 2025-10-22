package io.github.jb.domain

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.{CodecMakerConfig, JsonCodecMaker}

import java.util.UUID
import java.time.Instant

case class User(
    id: UUID,
    email: String,
    username: String,
    passwordHash: String,
    firstName: Option[String],
    lastName: Option[String],
    isActive: Boolean,
    createdAt: Instant,
    updatedAt: Instant
)

case class UserCreate(
    email: String,
    username: String,
    password: String,
    firstName: Option[String],
    lastName: Option[String]
)

case class UserResponse(
    id: UUID,
    email: String,
    username: String,
    firstName: Option[String],
    lastName: Option[String],
    isActive: Boolean,
    createdAt: Instant
)

case class UserStatusUpdate(isActive: Boolean)

case class LoginRequest(
    email: String,
    password: String
)

case class Tokens(
    accessToken: String,
    refreshToken: String,
    tokenType: String = "Bearer"
)

case class AuthResponse(
    tokens: Tokens,
    user: UserResponse
)

case class AccessTokenClaims(userId: UUID, email: String, username: String)
case class RefreshTokenClaims(userId: UUID, tokenType: String = "refresh")

sealed trait ApiError extends Product with Serializable {
  def message: String
}

object ApiError {

  case class UserAlreadyExists(email: String, override val message: String = "User with this email already exists")
      extends ApiError
  case class InvalidCredentials(override val message: String = "Invalid credentials") extends ApiError
  case class InvalidRefreshToken(override val message: String = "Invalid refresh token") extends ApiError
  case class UserNotFound(id: UUID, override val message: String = "User not found") extends ApiError
  case class InvalidOrExpiredToken(override val message: String = "Invalid or expired token") extends ApiError
  case class AccountDeactivated(override val message: String = "Account has been deactivated") extends ApiError
  case class InternalServerError(cause: String, override val message: String = "Internal server error") extends ApiError
}
