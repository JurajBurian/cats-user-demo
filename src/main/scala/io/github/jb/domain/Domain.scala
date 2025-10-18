package io.github.jb.domain

import java.util.UUID
import java.time.Instant
import com.github.plokhotnyuk.jsoniter_scala.macros.*
import com.github.plokhotnyuk.jsoniter_scala.core.*
import sttp.model.StatusCode

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

enum ApiError(message: String) extends Throwable(message) {

  case UserAlreadyExists(email: String)
      extends ApiError(s"User with this email already exists: $email")

  case InvalidCredentials extends ApiError("Invalid credentials")

  case InvalidRefreshToken extends ApiError("Invalid refresh token")

  case UserNotFound(id: UUID) extends ApiError(s"User not found: $id")

  case InvalidOrExpiredToken extends ApiError("Invalid or expired token")

  case AccountDeactivated extends ApiError("Account has been deactivated")

  case UnknownError(cause: String) extends ApiError(s"Unknown error, cuse: $cause")
}


// JSON codecs
given JsonValueCodec[UserCreate] = JsonCodecMaker.make
given JsonValueCodec[UserResponse] = JsonCodecMaker.make
given JsonValueCodec[List[UserResponse]] = JsonCodecMaker.make
given JsonValueCodec[UserStatusUpdate] = JsonCodecMaker.make
given JsonValueCodec[LoginRequest] = JsonCodecMaker.make
given JsonValueCodec[Tokens] = JsonCodecMaker.make
given JsonValueCodec[AuthResponse] = JsonCodecMaker.make
given JsonValueCodec[AccessTokenClaims] = JsonCodecMaker.make
given JsonValueCodec[RefreshTokenClaims] = JsonCodecMaker.make
given JsonValueCodec[Boolean] = JsonCodecMaker.make
