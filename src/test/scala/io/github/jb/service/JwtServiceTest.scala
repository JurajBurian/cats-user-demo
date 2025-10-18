package io.github.jb.service

import cats.effect.SyncIO
import io.github.jb.config.JwtConfig
import munit.FunSuite

import java.util.UUID
import io.github.jb.domain.*

import java.time.Instant

class JwtServiceTest extends FunSuite {

  val jwtConfig = JwtConfig(
    secretKey = "test-secret-key-very-long-and-secure-for-testing",
    accessTokenExpiration = "15 minutes",
    refreshTokenExpiration = "30 days"
  )

  val jwtService = new JwtServiceImpl[SyncIO](jwtConfig)

  val testUser = User(
    id = UUID.randomUUID(),
    email = "test@example.com",
    username = "testuser",
    passwordHash = "hash",
    firstName = Some("Test"),
    lastName = Some("User"),
    isActive = true,
    createdAt = Instant.now(),
    updatedAt = Instant.now()
  )

  test("generate and validate access token") {
    for {
      token <- jwtService.generateAccessToken(testUser)
      claims <- jwtService.validateAndExtractAccessToken(token)
    } yield {
      assert(claims.isDefined)
      assertEquals(claims.map(_.userId), Some(testUser.id))
      assertEquals(claims.map(_.email), Some(testUser.email))
      assertEquals(claims.map(_.username), Some(testUser.username))
    }
  }

  test("generate and validate refresh token") {
    for {
      token <- jwtService.generateRefreshToken(testUser.id)
      claims <- jwtService.validateAndExtractRefreshToken(token)
    } yield {
      assert(claims.isDefined)
      assertEquals(claims.map(_.userId), Some(testUser.id))
      assertEquals(claims.map(_.tokenType), Some("refresh"))
    }
  }

  test("fail validation with invalid token") {
    for {
      claims <- jwtService.validateAndExtractAccessToken("invalid.token.here")
    } yield assertEquals(claims, None)
  }

  test("fail validation with wrong secret key") {
    val wrongJwtService = new JwtServiceImpl[SyncIO](jwtConfig.copy(secretKey = "wrong-key"))

    for {
      token <- jwtService.generateAccessToken(testUser)
      claims <- wrongJwtService.validateAndExtractAccessToken(token)
    } yield assertEquals(claims, None)
  }
}
