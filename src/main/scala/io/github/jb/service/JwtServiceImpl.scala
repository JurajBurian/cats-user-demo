package io.github.jb.service

import cats.effect.Sync
import cats.effect.Clock
import cats.syntax.all.*
import com.github.plokhotnyuk.jsoniter_scala.core.{readFromString, writeToString}

import java.time.Instant
import java.util.UUID
import io.github.jb.config.JwtConfig
import io.github.jb.domain.*
import io.github.jb.domain.given
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}

class JwtServiceImpl[F[_]](config: JwtConfig)(using sync: Sync[F], clock: Clock[F]) extends JwtService[F] {

  private val algorithms = List(JwtAlgorithm.HS256)

  private def currentTime: F[Instant] = Clock[F].realTimeInstant

  def generateAccessToken(user: User): F[String] = currentTime.map { now =>
    val claims = AccessTokenClaims(
      userId = user.id,
      email = user.email,
      username = user.username
    )
    val jsonClaims = writeToString(claims)

    val jwtClaims = JwtClaim(
      expiration = Some(now.plusSeconds(15 * 60).getEpochSecond), // 15 minutes
      issuedAt = Some(now.getEpochSecond),
      subject = Some(user.id.toString),
      content = jsonClaims
    )
    Jwt.encode(jwtClaims, config.secretKey, algorithms.head)
  }

  def generateRefreshToken(userId: UUID): F[String] = currentTime.map { now =>
    val claims = RefreshTokenClaims(userId = userId)
    val jsonClaims = writeToString(claims)
    val jwtClaims = JwtClaim(
      expiration = Some(now.plusSeconds(30 * 24 * 60 * 60).getEpochSecond), // 30 days
      issuedAt = Some(now.getEpochSecond),
      subject = Some(userId.toString),
      content = jsonClaims
    )
    Jwt.encode(jwtClaims, config.secretKey, algorithms.head)
  }

  def validateAndExtractAccessToken(token: String): F[Option[AccessTokenClaims]] =
    sync.delay {
      if (Jwt.isValid(token, config.secretKey, algorithms)) {
        Jwt.decode(token, config.secretKey, algorithms).toOption.flatMap { claim =>
          scala.util.Try(readFromString[AccessTokenClaims](claim.content)).toOption
        }
      } else None
    }

  def validateAndExtractRefreshToken(token: String): F[Option[RefreshTokenClaims]] =
    sync.delay {
      if (Jwt.isValid(token, config.secretKey, algorithms)) {
        Jwt.decode(token, config.secretKey, algorithms).toOption.flatMap { claim =>
          scala.util.Try(readFromString[RefreshTokenClaims](claim.content)).toOption
        }
      } else None
    }
}
