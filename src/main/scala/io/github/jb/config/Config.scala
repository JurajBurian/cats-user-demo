package io.github.jb.config

import cats.effect.IO
import pureconfig.*
import pureconfig.module.catseffect.syntax.*

/* see typeclasses derivation:
https://docs.scala-lang.org/scala3/reference/contextual/derivation.html
 */

case class HttpConfig(host: String, port: Int) derives ConfigReader

//object HttpConfig {
//  given ConfigReader[HttpConfig] = ConfigReader.derived[HttpConfig]
//}

case class DatabaseConfig(
    url: String,
    user: String,
    password: String,
    driver: String,
    connectionPoolSize: Int
) derives ConfigReader

case class JwtConfig(
    secretKey: String,
    accessTokenExpiration: String,
    refreshTokenExpiration: String
) derives ConfigReader

case class BcryptConfig(cost: Int) derives ConfigReader

case class AppConfig(
    http: HttpConfig,
    database: DatabaseConfig,
    jwt: JwtConfig,
    bcrypt: BcryptConfig
) derives ConfigReader

object Config {
  def load: IO[AppConfig] = {
    ConfigSource.default
      .at("app") // Look for configuration under the "app" key
      .loadF[IO, AppConfig]()
  }
}
