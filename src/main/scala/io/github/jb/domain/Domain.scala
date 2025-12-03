package io.github.jb.domain

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

transparent trait Err {
  def message: String
}

case class UserAlreadyExists(email: String, message: String = "User with this email already exists") extends Err
case class InvalidCredentials(message: String = "Invalid credentials") extends Err
case class InvalidOrExpiredToken(message: String = "Invalid or expired token") extends Err
case class InvalidOrExpiredRefreshToken(message: String = "Invalid refresh token") extends Err
case class UserNotFound(id: UUID, message: String = "User not found") extends Err
case class AccountDeactivated(message: String = "Account has been deactivated") extends Err
case class InternalServerError(cause: String, message: String, th: Throwable) extends Err

object InternalServerError {
  def apply(cause: Throwable): InternalServerError =
    InternalServerError(cause.getMessage, "Internal server error", cause)
}
