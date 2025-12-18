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

  extension [A](fa: F[A])
    private def attemptDb[E]: EitherT[F, E | InternalServerErrorWithTh, A] =
      fa.attemptT.leftMap(ex => InternalServerErrorWithTh(ex))

  def create[E](userCreate: UserCreate, passwordHash: String): EitherT[F, E | InternalServerErrorWithTh, User] =
    sql"""
         |INSERT INTO users (email, username, password_hash, first_name, last_name)
         |VALUES (${userCreate.email}, ${userCreate.username}, $passwordHash,
         |       ${userCreate.firstName}, ${userCreate.lastName})
         |RETURNING id, email, username, password_hash, first_name, last_name,
                is_active, created_at, updated_at""".stripMargin
      .query[User]
      .unique
      .transact(xa)
      .attemptDb[E]

  def findByEmail[E](email: String): EitherT[F, E | InternalServerErrorWithTh, Option[User]] =
    sql"""
         |SELECT id, email, username, password_hash, first_name, last_name,
         |       is_active, created_at, updated_at
         |FROM users
         |WHERE email = $email""".stripMargin
      .query[User]
      .option
      .transact(xa)
      .attemptDb[E]

  def findById[E](id: UUID): EitherT[F, E | InternalServerErrorWithTh, Option[User]] =
    sql"""
         |SELECT id, email, username, password_hash, first_name, last_name,
         |       is_active, created_at, updated_at
         |FROM users
         |WHERE id = $id""".stripMargin
      .query[User]
      .option
      .transact(xa)
      .attemptDb[E]

  def updateStatus[E](id: UUID, isActive: Boolean): EitherT[F, E | InternalServerErrorWithTh, Boolean] =
    sql"""
         |UPDATE users
         |SET is_active = $isActive, updated_at = CURRENT_TIMESTAMP
         |WHERE id = $id""".stripMargin.update.run
      .transact(xa)
      .attemptDb[E]
      .map(_ > 0)

  def findActive[E](offset: Long, count: Long): EitherT[F, E | InternalServerErrorWithTh, List[User]] =
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
      .attemptDb[E]
}
