package bank.routes

import cats._
import cats.data._
import cats.effect.Sync
import cats.syntax.option._
import org.http4s.{HttpRoutes, Request, Response}

object HttpErrorHandler {
  def apply[F[_]: Sync, E <: Throwable](
    routes: HttpRoutes[EitherT[F, E, *]]
  )(handler: E => F[Response[F]]): HttpRoutes[F] =
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
