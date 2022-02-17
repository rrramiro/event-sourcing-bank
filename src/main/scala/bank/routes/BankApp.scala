package bank.routes

import org.http4s._
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.server.Router
import zio.Task
import zio.interop.catz._
import zio.interop.catz.implicits._

class BankApp(routes: HttpRoutes[Task]) {
  val router: HttpApp[Task] = Router("/api" -> routes).orNotFound
}
