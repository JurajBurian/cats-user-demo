package io.github.jb.domain

import io.circe.Codec

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
) derives Codec.AsObject

case class UserResponse(
    id: UUID,
    email: String,
    username: String,
    firstName: Option[String],
    lastName: Option[String],
    isActive: Boolean,
    createdAt: Instant
) derives Codec.AsObject

case class UserStatusUpdate(isActive: Boolean) derives Codec.AsObject

case class LoginRequest(
    email: String,
    password: String
) derives Codec.AsObject

case class Tokens(
    accessToken: String,
    refreshToken: String,
    tokenType: String = "Bearer"
) derives Codec.AsObject

case class AuthResponse(
    tokens: Tokens,
    user: UserResponse
) derives Codec.AsObject

case class AccessTokenClaims(userId: UUID, email: String, username: String) derives Codec.AsObject

case class RefreshTokenClaims(userId: UUID, tokenType: String = "refresh") derives Codec.AsObject
