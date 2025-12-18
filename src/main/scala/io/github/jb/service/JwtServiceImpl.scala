package io.github.jb.service

import cats.data.EitherT
import cats.effect.Sync
import cats.effect.Clock
import cats.syntax.all.*
import io.circe.parser._
import io.circe.syntax._

import java.time.Instant
import java.util.UUID
import io.github.jb.config.JwtConfig
import io.github.jb.domain.*
import io.github.jb.domain.given
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}

class JwtServiceImpl[F[_]](config: JwtConfig)(using sync: Sync[F], clock: Clock[F]) extends JwtService[F] {

  private val algorithms = List(JwtAlgorithm.HS256)

  private def currentTime: F[Instant] = Clock[F].realTimeInstant

  def generateAccessToken[E](user: User): EitherT[F, E, String] =
    EitherT.liftF(currentTime.map { now =>
      val claims = AccessTokenClaims(
        userId = user.id,
        email = user.email,
        username = user.username
      )
      val jsonClaims = claims.asJson.noSpaces

      val jwtClaims = JwtClaim(
        expiration = Some(now.plusSeconds(15 * 60).getEpochSecond), // 15 minutes
        issuedAt = Some(now.getEpochSecond),
        subject = Some(user.id.toString),
        content = jsonClaims
      )
      Jwt.encode(jwtClaims, config.secretKey, algorithms.head)
    })

  def generateRefreshToken[E](userId: UUID): EitherT[F, E, String] = EitherT.liftF(currentTime.map { now =>
    val claims = RefreshTokenClaims(userId = userId)
    val jsonClaims = claims.asJson.noSpaces
    val jwtClaims = JwtClaim(
      expiration = Some(now.plusSeconds(30 * 24 * 60 * 60).getEpochSecond), // 30 days
      issuedAt = Some(now.getEpochSecond),
      subject = Some(userId.toString),
      content = jsonClaims
    )
    Jwt.encode(jwtClaims, config.secretKey, algorithms.head)
  })

  def generateTokens[E](user: User): EitherT[F, E, Tokens] = generateAccessToken(user).flatMap { p1 =>
    generateRefreshToken(user.id).map(p2 => Tokens(p1, p2))
  }

  def validateAndExtractAccessToken(token: String): EitherT[F, InvalidOrExpiredToken, AccessTokenClaims] =
    if (Jwt.isValid(token, config.secretKey, algorithms)) {
      Jwt.decode(token, config.secretKey, algorithms).toOption.flatMap { claim =>
        decode[AccessTokenClaims](claim.content).toOption
      } match {
        case Some(claims) => EitherT.rightT(claims)
        case None         => EitherT.leftT(InvalidOrExpiredToken())
      }
    } else {
      EitherT.leftT(InvalidOrExpiredToken())
    }

  def validateAndExtractRefreshToken(token: String): EitherT[F, InvalidOrExpiredRefreshToken, RefreshTokenClaims] =
    if (Jwt.isValid(token, config.secretKey, algorithms)) {
      Jwt.decode(token, config.secretKey, algorithms).toOption.flatMap { claim =>
        decode[RefreshTokenClaims](claim.content).toOption
      } match {
        case Some(claims) => EitherT.rightT(claims)
        case None         => EitherT.leftT(InvalidOrExpiredRefreshToken())
      }
    } else EitherT.leftT(InvalidOrExpiredRefreshToken())
}
