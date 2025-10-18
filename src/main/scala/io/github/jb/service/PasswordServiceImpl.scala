package io.github.jb.service

import cats.effect.Sync
import at.favre.lib.crypto.bcrypt.BCrypt
import io.github.jb.domain.PasswordService

class PasswordServiceImpl[F[_]](cost: Int)(using sync: Sync[F]) extends PasswordService[F] {

  def hashPassword(password: String): F[String] =
    sync.delay {
      BCrypt.withDefaults().hashToString(cost, password.toCharArray)
    }

  def verifyPassword(password: String, hash: String): F[Boolean] =
    sync.delay {
      BCrypt.verifyer().verify(password.toCharArray, hash).verified
    }
}
