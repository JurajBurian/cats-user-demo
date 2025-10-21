package io.github.jb.service

import cats.Applicative
import cats.effect.IO
import cats.mtl.Handle
import io.github.jb.domain.ApiError

class CustomHandle extends Handle[IO, ApiError] {
  override def applicative: Applicative[IO] = cats.effect.IO.asyncForIO

  override def handleWith[A](fa: IO[A])(f: ApiError => IO[A]): IO[A] =
    fa.recoverWith {
      case e: ApiError => f(e)
      case other       => IO.raiseError(other) // re-raise non-ApiError exceptions
    }

  override def raise[E2 <: ApiError, A](e: E2): IO[A] =
    IO.raiseError(e)
}
