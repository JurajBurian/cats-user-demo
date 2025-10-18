package io.github.jb

import cats.MonadError
import cats.effect.IO
import io.github.jb.domain.ApiError

class CustomMonadError extends MonadError[IO, ApiError] {
  def pure[A](x: A): IO[A] = IO.pure(x)

  def flatMap[A, B](fa: IO[A])(f: A => IO[B]): IO[B] = fa.flatMap(f)

  def tailRecM[A, B](a: A)(f: A => IO[Either[A, B]]): IO[B] =
    IO.asyncForIO.tailRecM(a)(f)

  def raiseError[A](e: ApiError): IO[A] =
    IO.raiseError(e)

  def handleErrorWith[A](fa: IO[A])(f: ApiError => IO[A]): IO[A] =
    fa.handleErrorWith {
      case apiError: ApiError => f(apiError)
      case other: Throwable   => f(ApiError.UnknownError(other.getMessage))
    }
}
