package io.github.jb.repository

import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import cats.effect.Async
import cats.syntax.all.*
import java.util.UUID
import java.time.Instant
import io.github.jb.domain.*

class DoobieUserRepository[F[_]: Async](xa: Transactor[F]) extends UserRepository[F] {

  def create(userCreate: UserCreate, passwordHash: String): F[User] =
    sql"""
      |INSERT INTO users (email, username, password_hash, first_name, last_name)
      |VALUES (${userCreate.email}, ${userCreate.username}, $passwordHash,
      |       ${userCreate.firstName}, ${userCreate.lastName})
      |RETURNING id, email, username, password_hash, first_name, last_name,
                is_active, created_at, updated_at""".stripMargin
      .query[User]
      .unique
      .transact(xa)

  def findByEmail(email: String): F[Option[User]] =
    sql"""
         |SELECT id, email, username, password_hash, first_name, last_name,
         |       is_active, created_at, updated_at
         |FROM users
         |WHERE email = $email""".stripMargin
      .query[User]
      .option
      .transact(xa)

  def findById(id: UUID): F[Option[User]] =
    sql"""
         |SELECT id, email, username, password_hash, first_name, last_name, is_active, created_at, updated_at
         |FROM users
         |WHERE id = $id""".stripMargin
      .query[User]
      .option
      .transact(xa)

  def updateStatus(id: UUID, isActive: Boolean): F[Boolean] =
    sql"""
         |UPDATE users
         |SET is_active = $isActive, updated_at = CURRENT_TIMESTAMP
         |WHERE id = $id""".stripMargin.update.run
      .transact(xa)
      .map(_ > 0)

  def findActive(offset: Long, count: Long): F[List[User]] =
    sql"""
         |SELECT id, email, username, password_hash, first_name, last_name,is_active, created_at, updated_at
         |FROM users
         |WHERE is_active = true
         |ORDER BY created_at DESC
         |LIMIT $count OFFSET $offset""".stripMargin
      .query[User]
      .to[List]
      .transact(xa)
}
