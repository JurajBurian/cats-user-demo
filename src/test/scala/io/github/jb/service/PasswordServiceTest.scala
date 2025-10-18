package io.github.jb.service

import cats.effect.SyncIO
import munit.FunSuite

class PasswordServiceTest extends FunSuite {

  val passwordService = new PasswordServiceImpl[SyncIO](12)

  test("hash and verify password successfully") {
    for {
      hash <- passwordService.hashPassword("testPassword123")
      isValid <- passwordService.verifyPassword("testPassword123", hash)
    } yield assert(isValid)
  }

  test("fail verification with wrong password") {
    for {
      hash <- passwordService.hashPassword("testPassword123")
      isValid <- passwordService.verifyPassword("wrongPassword", hash)
    } yield assert(!isValid)
  }

  test("different hashes for same password") {
    for {
      hash1 <- passwordService.hashPassword("samePassword")
      hash2 <- passwordService.hashPassword("samePassword")
      isValid1 <- passwordService.verifyPassword("samePassword", hash1)
      isValid2 <- passwordService.verifyPassword("samePassword", hash2)
    } yield {
      assert(isValid1)
      assert(isValid2)
      assert(hash1 != hash2) // Different salts should produce different hashes
    }
  }
}
