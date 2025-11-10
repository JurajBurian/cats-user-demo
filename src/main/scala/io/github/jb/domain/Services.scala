package io.github.jb.domain

import cats.data.EitherT

import java.util.UUID

trait UserRepository[F[_]] {
  def create[E](userCreate: UserCreate, passwordHash: String): EitherT[F, E | InternalServerError, User]
  def findByEmail[E](email: String): EitherT[F, E | InternalServerError, Option[User]]
  def findById[E](id: UUID): EitherT[F, E | InternalServerError, Option[User]]
  def updateStatus[E](id: UUID, isActive: Boolean): EitherT[F, E | InternalServerError, Boolean]
  def findActive[E](offset: Long, count: Long): EitherT[F, E | InternalServerError, List[User]]
}

trait JwtService[F[_]] {
  def generateAccessToken[E](user: User): EitherT[F, E, String]
  def generateRefreshToken[E](userId: UUID): EitherT[F, E, String]
  def generateTokens[E](userId: User): EitherT[F, E, Tokens]
  def validateAndExtractAccessToken(token: String): EitherT[F, InvalidOrExpiredToken, AccessTokenClaims]
  def validateAndExtractRefreshToken(token: String): EitherT[F, InvalidOrExpiredRefreshToken, RefreshTokenClaims]
}

trait PasswordService[F[_]] {
  def hashPassword[E](password: String): EitherT[F, E, String]
  def verifyPassword[E](password: String, hash: String): EitherT[F, E, Boolean]
}

trait UserService[F[_]] {
  def createUser(userCreate: UserCreate): EitherT[F, InternalServerError | UserAlreadyExists, UserResponse]
  def login(
      loginRequest: LoginRequest
  ): EitherT[F, InternalServerError | AccountDeactivated | InvalidCredentials, AuthResponse]
  def refreshTokens(
      refreshToken: String
  ): EitherT[F, InternalServerError | AccountDeactivated | InvalidOrExpiredRefreshToken | UserNotFound, AuthResponse]

  def getUser[E](id: UUID): EitherT[F, E | InternalServerError | UserNotFound, UserResponse]
  def updateUserStatus[E](id: UUID, isActive: Boolean): EitherT[F, E | InternalServerError, Boolean]
  def listActiveUsers[E](offset: Long, count: Long): EitherT[F, E | InternalServerError, List[UserResponse]]
  def validateUserForAccess[E](
      token: String
  ): EitherT[F, E | InternalServerError | InvalidOrExpiredToken | AccountDeactivated | UserNotFound, UserResponse]
}
