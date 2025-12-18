package io.github.jb.domain

import cats.data.EitherT

import java.util.UUID

trait UserRepository[F[_]] {
  def create[E](userCreate: UserCreate, passwordHash: String): EitherT[F, E | InternalServerErrorWithTh, User]
  def findByEmail[E](email: String): EitherT[F, E | InternalServerErrorWithTh, Option[User]]
  def findById[E](id: UUID): EitherT[F, E | InternalServerErrorWithTh, Option[User]]
  def updateStatus[E](id: UUID, isActive: Boolean): EitherT[F, E | InternalServerErrorWithTh, Boolean]
  def findActive[E](offset: Long, count: Long): EitherT[F, E | InternalServerErrorWithTh, List[User]]
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
  def createUser(userCreate: UserCreate): EitherT[F, InternalServerErrorWithTh | UserAlreadyExists | ValidationError, UserResponse]
  def login(
      loginRequest: LoginRequest
  ): EitherT[F, InternalServerErrorWithTh | AccountDeactivated | InvalidCredentials, AuthResponse]
  def refreshTokens(
      refreshToken: String
  ): EitherT[
    F,
    InternalServerErrorWithTh | AccountDeactivated | InvalidOrExpiredRefreshToken | UserNotFound,
    AuthResponse
  ]

  def getUser[E](id: UUID): EitherT[F, E | InternalServerErrorWithTh | UserNotFound, UserResponse]
  def updateUserStatus[E](id: UUID, isActive: Boolean): EitherT[F, E | InternalServerErrorWithTh, Boolean]
  def listActiveUsers[E](offset: Long, count: Long): EitherT[F, E | InternalServerErrorWithTh, List[UserResponse]]
  def validateUserForAccess[E](
      token: String
  ): EitherT[F, E | InternalServerErrorWithTh | InvalidOrExpiredToken | AccountDeactivated | UserNotFound, UserResponse]
}
