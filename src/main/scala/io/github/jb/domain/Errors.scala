package io.github.jb.domain

import io.circe.Codec
import io.circe.generic.extras.auto.*
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.*
import io.circe.syntax.*
import java.util.UUID

given Configuration = Configuration.default.withDiscriminator("type")

transparent sealed trait Err {
  def id: String
}

case class UserAlreadyExists(email: String, id: String = "UserAlreadyExists") extends Err
    derives Codec.AsObject

case class InvalidCredentials(id: String = "InvalidCredentials") extends Err derives Codec.AsObject

case class InvalidOrExpiredToken(id: String = "InvalidOrExpiredToken") extends Err derives Codec.AsObject

case class InvalidOrExpiredRefreshToken(id: String = "InvalidOrExpiredRefreshToken") extends Err derives Codec.AsObject

case class UserNotFound(uuid: UUID, id: String = "UserNotFound") extends Err derives Codec.AsObject

case class AccountDeactivated(id: String = "AccountDeactivated") extends Err derives Codec.AsObject

case class InternalServerError(cause: String, id: String = "InternalServerError") extends Err derives Codec.AsObject

case class InternalServerErrorWithTh(body: InternalServerError, th: Throwable)

object InternalServerErrorWithTh {
  def apply(cause: Throwable): InternalServerErrorWithTh =
    InternalServerErrorWithTh(InternalServerError(cause.getMessage, "InternalServerError"), cause)
}

enum ValidationErrorReason derives Codec.AsObject {
  case InvalidEmailFormat
  case EmailTooShort
  case EmailTooLong
  case UsernameTooShort
  case UsernameInvalidCharacters
  case PasswordTooShort
  case PasswordMissingUppercase
  case PasswordMissingLowercase
  case PasswordMissingNumber
  case PasswordMissingSpecialChar
}

case class ValidationError(reasons: List[ValidationErrorReason], id: String = "ValidationError") extends Err derives Codec.AsObject
