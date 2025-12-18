package io.github.jb.service

import cats.Monad
import cats.data.EitherT
import java.util.UUID
import io.github.jb.domain.*
import scala.util.matching.Regex

class UserServiceImpl[F[_]: Monad](
    userRepo: UserRepository[F],
    jwtService: JwtService[F],
    passwordService: PasswordService[F]
) extends UserService[F] {

  def createUser(userCreate: UserCreate): EitherT[F, InternalServerErrorWithTh | UserAlreadyExists | ValidationError, UserResponse] = {
    for {
      _ <- validateUserCreate(userCreate)
      existingUser <- userRepo.findByEmail(userCreate.email)
      _ <- EitherT.cond(existingUser.isEmpty, (), UserAlreadyExists(userCreate.email))
      passwordHash <- passwordService.hashPassword(userCreate.password)
      user <- userRepo.create(userCreate, passwordHash)
    } yield toUserResponse(user)
  }

  def login(
      loginRequest: LoginRequest
  ): EitherT[F, InternalServerErrorWithTh | AccountDeactivated | InvalidCredentials, AuthResponse] = {
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
    InternalServerErrorWithTh | AccountDeactivated | InvalidOrExpiredRefreshToken | UserNotFound,
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

  def getUser[E](id: UUID): EitherT[F, E | InternalServerErrorWithTh | UserNotFound, UserResponse] = {
    for {
      userOpt <- userRepo.findById(id)
      user <- EitherT.fromOption(userOpt, UserNotFound(id))
    } yield toUserResponse(user)
  }

  def updateUserStatus[E](id: UUID, isActive: Boolean): EitherT[F, E | InternalServerErrorWithTh, Boolean] =
    userRepo.updateStatus(id, isActive)

  def listActiveUsers[E](offset: Long, count: Long): EitherT[F, E | InternalServerErrorWithTh, List[UserResponse]] =
    userRepo.findActive(offset, count).map(_.map(toUserResponse))

  def validateUserForAccess[E](
      token: String
  ): EitherT[
    F,
    E | InternalServerErrorWithTh | InvalidOrExpiredToken | AccountDeactivated | UserNotFound,
    UserResponse
  ] = {
    for {
      accessClaims <- jwtService.validateAndExtractAccessToken(token)
      userOpt <- userRepo.findById(accessClaims.userId)
      user <- EitherT.fromOption(userOpt, UserNotFound(accessClaims.userId))
      _ <- isActive(user)
    } yield toUserResponse(user)
  }

  private def validateUserCreate(userCreate: UserCreate): EitherT[F, ValidationError, Unit] = {
    val errors = scala.collection.mutable.ListBuffer[ValidationErrorReason]()
    
    // Email validation
    val emailRegex = """^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$""".r
    if (!emailRegex.matches(userCreate.email)) {
      errors += ValidationErrorReason.InvalidEmailFormat
    }
    if (userCreate.email.length < 5) {
      errors += ValidationErrorReason.EmailTooShort
    }
    if (userCreate.email.length > 254) {
      errors += ValidationErrorReason.EmailTooLong
    }
    
    // Username validation
    if (userCreate.username.length < 4) {
      errors += ValidationErrorReason.UsernameTooShort
    }
    if (!userCreate.username.matches("^[a-zA-Z0-9_]+$")) {
      errors += ValidationErrorReason.UsernameInvalidCharacters
    }
    
    // Password validation
    if (userCreate.password.length < 8) {
      errors += ValidationErrorReason.PasswordTooShort
    }
    if (!userCreate.password.matches(".*[A-Z].*")) {
      errors += ValidationErrorReason.PasswordMissingUppercase
    }
    if (!userCreate.password.matches(".*[a-z].*")) {
      errors += ValidationErrorReason.PasswordMissingLowercase
    }
    if (!userCreate.password.matches(".*\\d.*")) {
      errors += ValidationErrorReason.PasswordMissingNumber
    }
    if (!userCreate.password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")) {
      errors += ValidationErrorReason.PasswordMissingSpecialChar
    }
    
    EitherT.cond(errors.isEmpty, (), ValidationError(errors.toList))
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
