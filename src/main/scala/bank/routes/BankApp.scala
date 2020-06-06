package bank.routes

import cats.effect.Sync
import org.http4s._
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.server.Router

class BankApp[F[_]: Sync](routes: HttpRoutes[F]) {
  val router: HttpApp[F] = Router("/api" -> routes).orNotFound
}
