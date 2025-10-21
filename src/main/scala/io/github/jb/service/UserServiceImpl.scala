package io.github.jb.service

import cats.Monad
import cats.mtl.Raise
import cats.syntax.all.*
import java.util.UUID
import io.github.jb.domain.*
import io.github.jb.domain.UserService
import io.github.jb.domain.ApiError

class UserServiceImpl[F[_]](
    userRepo: UserRepository[F],
    jwtService: JwtService[F],
    passwordService: PasswordService[F]
)(using M: Monad[F], R: Raise[F, ApiError])
    extends UserService[F] {

  def createUser(userCreate: UserCreate): F[UserResponse] = {
    for {
      existingUser <- userRepo.findByEmail(userCreate.email)
      _ <- existingUser match {
        case Some(_) => R.raise(ApiError.UserAlreadyExists(userCreate.email))
        case None    => M.unit
      }

      passwordHash <- passwordService.hashPassword(userCreate.password)
      user <- userRepo.create(userCreate, passwordHash)
    } yield toUserResponse(user)
  }

  def login(loginRequest: LoginRequest): F[AuthResponse] = {
    for {
      userOpt <- userRepo.findByEmail(loginRequest.email)
      user <- userOpt match {
        case Some(user) => user.pure[F]
        case None       => R.raise(ApiError.InvalidCredentials)
      }

      _ <- validateUserStatus(user)

      isValid <- passwordService.verifyPassword(loginRequest.password, user.passwordHash)
      _ <- if (!isValid) R.raise(ApiError.InvalidCredentials) else Monad[F].unit

      accessToken <- jwtService.generateAccessToken(user)
      refreshToken <- jwtService.generateRefreshToken(user.id)
      userResponse = toUserResponse(user)
    } yield AuthResponse(Tokens(accessToken, refreshToken), userResponse)
  }

  def refreshTokens(refreshToken: String): F[AuthResponse] = {
    for {
      refreshClaimsOpt <- jwtService.validateAndExtractRefreshToken(refreshToken)
      refreshClaims <- refreshClaimsOpt match {
        case Some(claims) => claims.pure[F]
        case None         => R.raise(ApiError.InvalidRefreshToken)
      }

      userOpt <- userRepo.findById(refreshClaims.userId)
      user <- userOpt match {
        case Some(user) => user.pure[F]
        case None       => R.raise(ApiError.UserNotFound(refreshClaims.userId))
      }

      _ <- validateUserStatus(user)

      newAccessToken <- jwtService.generateAccessToken(user)
      newRefreshToken <- jwtService.generateRefreshToken(user.id)
      userResponse = toUserResponse(user)
    } yield AuthResponse(Tokens(newAccessToken, newRefreshToken), userResponse)
  }

  def getUser(id: UUID): F[UserResponse] = {
    userRepo.findById(id).flatMap {
      case Some(user) => toUserResponse(user).pure[F]
      case None       => R.raise(ApiError.UserNotFound(id))
    }
  }

  def updateUserStatus(id: UUID, isActive: Boolean): F[Boolean] = userRepo.updateStatus(id, isActive)

  def validateUserForAccess(token: String): F[User] = {
    for {
      accessClaimsOpt <- jwtService.validateAndExtractAccessToken(token)
      accessClaims <- accessClaimsOpt match {
        case Some(claims) => claims.pure[F]
        case None         => R.raise(ApiError.InvalidOrExpiredToken)
      }

      userOpt <- userRepo.findById(accessClaims.userId)
      user <- userOpt match {
        case Some(user) => user.pure[F]
        case None       => R.raise(ApiError.UserNotFound(accessClaims.userId))
      }

      _ <- validateUserStatus(user)
    } yield user
  }

  def listActiveUsers(offset: Long, count: Long): F[List[UserResponse]] =
    userRepo.findActive(offset, count).map(_.map(toUserResponse))

  private def validateUserStatus(user: User): F[Unit] =
    if (!user.isActive) R.raise(ApiError.AccountDeactivated) else Monad[F].unit

  private def toUserResponse(user: User): UserResponse =
    UserResponse(
      id = user.id,
      email = user.email,
      username = user.username,
      firstName = user.firstName,
      lastName = user.lastName,
      isActive = user.isActive,
      createdAt = user.createdAt
    )
}
