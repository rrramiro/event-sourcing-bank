package bank.routes

import cats._
import cats.data._
import cats.effect.Sync
import cats.syntax.option._
import org.http4s.{HttpRoutes, Request, Response}

abstract class HttpErrorHandler[F[_]: Sync, E <: Throwable] {
  def handler: E => F[Response[F]]
  def routes: HttpRoutes[EitherT[F, E, *]]

  def handle: HttpRoutes[F] =
    Kleisli { req: Request[F] =>
      OptionT[F, Response[F]] {
        routes
          .run(req.mapK(λ[F ~> EitherT[F, E, *]](EitherT.right[E](_))))
          .map(_.mapK(λ[EitherT[F, E, *] ~> F](_.valueOrF(Sync[F].raiseError))))
          .value
          .leftSemiflatMap(handler)
          .leftMap(_.some)
          .merge
      }
    }
}
