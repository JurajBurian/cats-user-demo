package io.github.jb.service

import at.favre.lib.crypto.bcrypt.BCrypt
import cats.Monad
import cats.data.EitherT
import io.github.jb.domain.*

class PasswordServiceImpl[F[_]: Monad](cost: Int) extends PasswordService[F] {

  def hashPassword[E](password: String): EitherT[F, E, String] =
    EitherT.pure {
      BCrypt.withDefaults().hashToString(cost, password.toCharArray)
    }

  def verifyPassword[E](password: String, hash: String): EitherT[F, E, Boolean] =
    EitherT.pure {
      BCrypt.verifyer().verify(password.toCharArray, hash).verified
    }
}
