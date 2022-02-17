package bank.routes

import cats.data._
import cats.syntax.option._
import org.http4s.{HttpRoutes, Response}
import zio.Task

import scala.reflect.ClassTag

object HttpErrorHandler {

  def apply[E <: Throwable: ClassTag](
    routes: HttpRoutes[Task]
  )(handler: E => Task[Response[Task]]): HttpRoutes[Task] =
    Kleisli { req =>
      OptionT {
        routes.run(req).value.catchNonFatalOrDie {
          case error: E => handler(error).map(_.some)
          case t        => Task.fail(t)
        }
      }
    }
}
