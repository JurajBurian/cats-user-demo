package io.github.jb.repository

import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import cats.effect.Async
import cats.syntax.all.*
import cats.data.EitherT
import java.util.UUID
import io.github.jb.domain.*

class DoobieUserRepository[F[_]: Async](xa: Transactor[F]) extends UserRepository[F] {

  private def handleDbError[E, A](fa: F[A]): EitherT[F, E | InternalServerError, A] = EitherT {
    fa.attempt.map {
      case Right(value) => Right(value)
      case Left(ex)     => Left(InternalServerError(ex.getMessage))
    }
  }

  def create[E](userCreate: UserCreate, passwordHash: String): EitherT[F, E| InternalServerError, User] =
    handleDbError {
      sql"""
           |INSERT INTO users (email, username, password_hash, first_name, last_name)
           |VALUES (${userCreate.email}, ${userCreate.username}, $passwordHash,
           |       ${userCreate.firstName}, ${userCreate.lastName})
           |RETURNING id, email, username, password_hash, first_name, last_name,
                  is_active, created_at, updated_at""".stripMargin
        .query[User]
        .unique
        .transact(xa)
    }

  def findByEmail[E](email: String): EitherT[F, E | InternalServerError, Option[User]] =
    handleDbError {
      sql"""
           |SELECT id, email, username, password_hash, first_name, last_name,
           |       is_active, created_at, updated_at
           |FROM users
           |WHERE email = $email""".stripMargin
        .query[User]
        .option
        .transact(xa)
    }

  def findById[E](id: UUID): EitherT[F, E | InternalServerError, Option[User]] =
    handleDbError {
      sql"""
           |SELECT id, email, username, password_hash, first_name, last_name,
           |       is_active, created_at, updated_at
           |FROM users
           |WHERE id = $id""".stripMargin
        .query[User]
        .option
        .transact(xa)
    }

  def updateStatus[E](id: UUID, isActive: Boolean): EitherT[F, E | InternalServerError, Boolean] =
    handleDbError {
      sql"""
           |UPDATE users
           |SET is_active = $isActive, updated_at = CURRENT_TIMESTAMP
           |WHERE id = $id""".stripMargin.update.run
        .transact(xa)
        .map(_ > 0)
    }

  def findActive[E](offset: Long, count: Long): EitherT[F, E | InternalServerError, List[User]] =
    handleDbError {
      sql"""
           |SELECT id, email, username, password_hash, first_name, last_name,
           |       is_active, created_at, updated_at
           |FROM users
           |WHERE is_active = true
           |ORDER BY created_at DESC
           |LIMIT $count OFFSET $offset""".stripMargin
        .query[User]
        .to[List]
        .transact(xa)
    }
}
