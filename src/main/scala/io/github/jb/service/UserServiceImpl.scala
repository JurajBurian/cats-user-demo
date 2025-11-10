package io.github.jb.service

import cats.Monad
import cats.data.EitherT
import cats.syntax.all.*
import java.util.UUID
import io.github.jb.domain.*

class UserServiceImpl[F[_]: Monad](
    userRepo: UserRepository[F],
    jwtService: JwtService[F],
    passwordService: PasswordService[F]
) extends UserService[F] {

  def createUser(userCreate: UserCreate): EitherT[F, InternalServerError | UserAlreadyExists, UserResponse] = {
    (for {
      existingUser <- userRepo.findByEmail(userCreate.email)
      _ <- EitherT.cond(existingUser.isEmpty, (), UserAlreadyExists(userCreate.email))
      passwordHash <- passwordService.hashPassword(userCreate.password)
      user <- userRepo.create(userCreate, passwordHash).leftMap(identity[InternalServerError])
    } yield toUserResponse(user))
  }

  def login(
      loginRequest: LoginRequest
  ): EitherT[F, InternalServerError | AccountDeactivated | InvalidCredentials, AuthResponse] = {
    for {
      userOpt <- userRepo.findByEmail(loginRequest.email)
      user <- EitherT.fromOption(userOpt, InvalidCredentials())
      _ <- isActive(user)
      isValid <- passwordService.verifyPassword(loginRequest.password, user.passwordHash)
      _ <- EitherT.cond(isValid, (), InvalidCredentials())
      tokens <- jwtService.generateTokens(user)
    } yield AuthResponse(tokens, toUserResponse(user))
  }

  def refreshTokens(
      refreshToken: String
  ): EitherT[
    F,
    InternalServerError | AccountDeactivated | InvalidOrExpiredRefreshToken | UserNotFound,
    AuthResponse
  ] = {
    for {
      refreshClaims <- jwtService.validateAndExtractRefreshToken(refreshToken)
      userOpt <- userRepo.findById(refreshClaims.userId)
      user <- EitherT.fromOption(userOpt, UserNotFound(refreshClaims.userId))
      tokens <- jwtService.generateTokens(user)
      _ <- isActive(user)
    } yield AuthResponse(tokens, toUserResponse(user))
  }

  def getUser[E](id: UUID): EitherT[F, E | InternalServerError | UserNotFound, UserResponse] = {
    for {
      userOpt <- userRepo.findById(id)
      user <- EitherT.fromOption(userOpt, UserNotFound(id))
    } yield toUserResponse(user)
  }

  def updateUserStatus[E](id: UUID, isActive: Boolean): EitherT[F, E | InternalServerError, Boolean] =
    userRepo.updateStatus(id, isActive)

  def listActiveUsers[E](offset: Long, count: Long): EitherT[F, E | InternalServerError, List[UserResponse]] =
    userRepo.findActive(offset, count).map(_.map(toUserResponse))

  def validateUserForAccess[E](
      token: String
  ): EitherT[F, E | InternalServerError | InvalidOrExpiredToken | AccountDeactivated | UserNotFound, UserResponse] = {
    for {
      accessClaims <- jwtService.validateAndExtractAccessToken(token)
      userOpt <- userRepo.findById(accessClaims.userId)
      user <- EitherT.fromOption(userOpt, UserNotFound(accessClaims.userId))
      _ <- isActive(user)
    } yield toUserResponse(user)
  }

  private inline def toUserResponse(user: User): UserResponse = {
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

  private inline def isActive[E](user: User): EitherT[F, E | AccountDeactivated, Unit] =
    EitherT.cond(user.isActive, (), AccountDeactivated())
}
