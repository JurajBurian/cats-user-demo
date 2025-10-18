package io.github.jb.domain

import java.util.UUID

trait UserRepository[F[_]] {
  def create(userCreate: UserCreate, passwordHash: String): F[User]
  def findByEmail(email: String): F[Option[User]]
  def findById(id: UUID): F[Option[User]]
  def updateStatus(id: UUID, isActive: Boolean): F[Boolean]
  def findActive(offset: Long, count: Long): F[List[User]]
}

trait JwtService[F[_]] {
  def generateAccessToken(user: User): F[String]
  def generateRefreshToken(userId: UUID): F[String]
  def validateAndExtractAccessToken(token: String): F[Option[AccessTokenClaims]]
  def validateAndExtractRefreshToken(token: String): F[Option[RefreshTokenClaims]]
}

trait PasswordService[F[_]] {
  def hashPassword(password: String): F[String]
  def verifyPassword(password: String, hash: String): F[Boolean]
}

trait UserService[F[_]] {
  def createUser(userCreate: UserCreate): F[UserResponse]
  def login(loginRequest: LoginRequest): F[AuthResponse]
  def refreshTokens(refreshToken: String): F[AuthResponse]
  def getUser(id: UUID): F[UserResponse]
  def updateUserStatus(id: UUID, isActive: Boolean): F[Boolean]
  def validateUserForAccess(token: String): F[User]
  def listActiveUsers(offset: Long, count: Long): F[List[UserResponse]]
}
