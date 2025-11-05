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

case class UserAlreadyExists(email: String, message: String = "User with this email already exists")
case class InvalidCredentials(message: String = "Invalid credentials")
case class InvalidOrExpiredToken(message: String = "Invalid or expired token")
case class InvalidOrExpiredRefreshToken(message: String = "Invalid refresh token")
case class UserNotFound(id: UUID, message: String = "User not found")
case class AccountDeactivated(message: String = "Account has been deactivated")
case class InternalServerError(cause: String, message: String = "Internal server error")
