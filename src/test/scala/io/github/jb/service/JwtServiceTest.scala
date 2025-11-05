package io.github.jb.service

import cats.effect.IO
import io.github.jb.config.JwtConfig
import munit.{CatsEffectSuite, FunSuite}

import java.util.UUID
import io.github.jb.domain.*
import munit.Clue.generate

import java.time.Instant

class JwtServiceTest extends CatsEffectSuite  {

  val jwtConfig = JwtConfig(
    secretKey = "test-secret-key-very-long-and-secure-for-testing",
    accessTokenExpiration = "15 minutes",
    refreshTokenExpiration = "30 days"
  )

  val jwtService = new JwtServiceImpl[IO](jwtConfig)

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
      cs <- jwtService.validateAndExtractAccessToken(token)
    } yield {
      val claims = cs.value
      assertEquals(claims.userId, testUser.id)
      assertEquals(claims.email, testUser.email)
      assertEquals(claims.username, testUser.username)
    }
  }

  test("generate and validate refresh token") {
    for {
      token <- jwtService.generateRefreshToken(testUser.id)
      cs <- jwtService.validateAndExtractRefreshToken(token)
    } yield {
      val claims = cs.value
      assertEquals(claims.userId, testUser.id)
      assertEquals(claims.tokenType, "refresh")
    }
  }

  test("fail validation with invalid token") {
    for {
      claims <- jwtService.validateAndExtractAccessToken("invalid.token.here").value
    } yield assert(claims.isLeft)
  }

  test("fail validation with wrong secret key") {
    val wrongJwtService = new JwtServiceImpl[IO](jwtConfig.copy(secretKey = "wrong-key"))

    for {
      token <- jwtService.generateAccessToken(testUser).value
      claims <- token match {
        case Left(value) => fail("Should have failed")
        case Right(value) => wrongJwtService.validateAndExtractAccessToken(value).value
      }
    } yield assert(claims.isLeft)
  }
}
