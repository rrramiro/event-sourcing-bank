package bank.routes

import cats.Functor
import cats.data._
import cats.effect.Sync
import cats.mtl.Raise
import cats.syntax.option._
import cats.syntax.functor._
import cats.syntax.applicativeError._
import org.http4s.{HttpRoutes, Response}

import scala.reflect.ClassTag

object HttpErrorHandler {

  def apply[F[_]: Sync, E <: Throwable: ClassTag](
    routes: Raise[F, E] => HttpRoutes[F]
  )(handler: E => F[Response[F]]): HttpRoutes[F] = {
    val raise: Raise[F, E] =
      new Raise[F, E] {
        override def functor: Functor[F]            = implicitly
        override def raise[E2 <: E, A](e: E2): F[A] = e.raiseError
      }
    Kleisli { req =>
      OptionT {
        routes(raise).run(req).value.handleErrorWith {
          case error: E => handler(error).map(_.some)
          case t        => t.raiseError
        }
      }
    }
  }
}
