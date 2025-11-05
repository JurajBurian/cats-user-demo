package io.github.jb.domain

import cats.data.EitherT

import java.util.UUID

trait UserRepository[F[_]] {
  def create(userCreate: UserCreate, passwordHash: String): EitherT[F, InternalServerError, User]
  def findByEmail(email: String): EitherT[F, InternalServerError, Option[User]]
  def findById(id: UUID): EitherT[F, InternalServerError, Option[User]]
  def updateStatus(id: UUID, isActive: Boolean): EitherT[F, InternalServerError, Boolean]
  def findActive(offset: Long, count: Long): EitherT[F, InternalServerError, List[User]]
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
  def getUser(id: UUID): EitherT[F, InternalServerError | UserNotFound, UserResponse]
  def updateUserStatus(id: UUID, isActive: Boolean): EitherT[F, InternalServerError, Boolean]
  def validateUserForAccess(
      token: String
  ): EitherT[F, InternalServerError | InvalidOrExpiredToken | AccountDeactivated | UserNotFound, UserResponse]
  def listActiveUsers(offset: Long, count: Long): EitherT[F, InternalServerError, List[UserResponse]]
}
