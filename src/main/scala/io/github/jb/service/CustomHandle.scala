package io.github.jb.service

import cats.Applicative
import cats.effect.IO
import cats.mtl.Handle
import io.github.jb.domain.ApiError

class CustomHandle extends Handle[IO, ApiError] {

  private case class InternalError(apiError: ApiError) extends Throwable(apiError.message)

  override def applicative: Applicative[IO] = cats.effect.IO.asyncForIO

  override def handleWith[A](fa: IO[A])(f: ApiError => IO[A]): IO[A] =
    fa.recoverWith {
      case e: InternalError =>
        f(e.apiError)
      case other =>
        f(ApiError.UnknownError(other.getMessage)) // re-raise non-ApiError as unknown error
    }

  override def raise[E2 <: ApiError, A](e: E2): IO[A] =
    IO.raiseError(new InternalError(e))
}
