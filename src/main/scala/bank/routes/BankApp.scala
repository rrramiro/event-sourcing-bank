package bank.routes

import bank.model.aggregates._
import cats.data.EitherT
import cats.effect.Sync
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.server.Router

class BankApp[F[_]: Sync](
  override val routes: HttpRoutes[EitherT[F, AggregateError, *]]
) extends HttpErrorHandler[F, AggregateError]
  with Http4sDsl[F] {

  val handler: AggregateError => F[Response[F]] = {
    case AggregateVersionError => InternalServerError()
    case AggregateNotFound     => NotFound()
  }

  val router: HttpApp[F] =
    http4sKleisliResponseSyntaxOptionT(Router("/api" -> handle)).orNotFound
}
