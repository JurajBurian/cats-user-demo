ThisBuild / version := "0.1.0"
ThisBuild / scalaVersion := "3.7.3"

ThisBuild / fork := true

lazy val root = (project in file("."))
  .settings(
    name := "user-management",
    libraryDependencies ++= Seq(
      // Cats & Cats Effect
      // "org.typelevel" %% "cats-core" % "2.13.0",
      "org.typelevel" %% "cats-effect" % "3.6.3",

      // HTTP Server
      "com.softwaremill.sttp.tapir" %% "tapir-netty-server-cats" % "1.11.49",
      "com.softwaremill.sttp.tapir" %% "tapir-jsoniter-scala" % "1.11.49",
      "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % "1.11.49",

      // Database
      // "org.tpolecat" %% "doobie-core" % "1.0.0-RC4",
      "org.tpolecat" %% "doobie-hikari" % "1.0.0-RC10",
      "org.tpolecat" %% "doobie-postgres" % "1.0.0-RC10",
      "org.flywaydb" % "flyway-database-postgresql" % "11.14.1",

      // JWT
      "com.github.jwt-scala" %% "jwt-core" % "11.0.3",

      // JSON
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core" % "2.38.3",
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % "2.38.3" % Provided,

      // Config
      "com.github.pureconfig" %% "pureconfig-core" % "0.17.9",
      "com.github.pureconfig" %% "pureconfig-generic-scala3" % "0.17.9",
      "com.github.pureconfig" %% "pureconfig-cats-effect" % "0.17.9",

      // Password hashing
      "at.favre.lib" % "bcrypt" % "0.10.2",

      // Logging
      "org.typelevel" %% "log4cats-slf4j" % "2.7.1",
      "ch.qos.logback" % "logback-classic" % "1.5.19",

      // Test dependencies
      "org.scalameta" %% "munit" % "1.2.1" % Test,
      "org.typelevel" %% "munit-cats-effect" % "2.1.0" % Test,
      "org.typelevel" %% "cats-effect-testkit" % "3.6.3" % Test,
      "org.tpolecat" %% "doobie-munit" % "1.0.0-RC10" % Test,
      "com.dimafeng" %% "testcontainers-scala-munit" % "0.43.0" % Test,
      "com.dimafeng" %% "testcontainers-scala-postgresql" % "0.43.0" % Test
    )
  )
