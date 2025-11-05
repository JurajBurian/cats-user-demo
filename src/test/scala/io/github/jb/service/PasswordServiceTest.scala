package io.github.jb.service

import cats.effect.IO
import munit.CatsEffectSuite

class PasswordServiceTest extends CatsEffectSuite {

  val passwordService = new PasswordServiceImpl[IO](12)

  test("hash and verify password successfully") {
    for {
      hash <- passwordService.hashPassword("testPassword123").value
      isValid <- hash match {
        case Left(_)     => fail("Hashing failed")
        case Right(hash) => passwordService.verifyPassword("testPassword123", hash).value
      }
    } yield {
      assert(isValid.isRight)
      assert(isValid.getOrElse(false))
    }
  }

  test("fail verification with wrong password") {
    for {
      hash <- passwordService.hashPassword("testPassword123").value
      isValid <- hash match {
        case Left(_) =>
          fail("Hashing failed")
        case Right(hash) =>
          passwordService.verifyPassword("wrongPassword", hash).value
      }
    } yield assert(!isValid.getOrElse(true))

  }

  test("different hashes for same password") {
    for {
      hash1 <- passwordService.hashPassword("samePassword").value
      hash2 <- passwordService.hashPassword("samePassword").value
    } yield {
      assert(hash1.getOrElse("wrong") != hash2.getOrElse("wrong")) // Different salts should produce different hashes
    }
  }
}
