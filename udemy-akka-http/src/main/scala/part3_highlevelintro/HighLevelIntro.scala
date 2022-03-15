package part3_highlevelintro

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer

object HighLevelIntro extends App {
  implicit val system = ActorSystem("HighLevelIntro")
  implicit val mat = ActorMaterializer()
  import system.dispatcher

  //directive
  import  akka.http.scaladsl.server.Directives._
  val simpleRoute =
    path("home"){           //DIRECTIVE
      complete(StatusCodes.OK)   //DIRECTIVE
    }

  println("server is ready")
  //Http().bindAndHandle(simpleRoute,"localhost",8080)

  val pathGetRoute : Route =
    path("home"){
      get{
        complete(StatusCodes.OK)
      }
    }
  //Http().bindAndHandle(pathGetRoute,"localhost",8080)

  //chaining diretives with ~
 val chainedRoutes =  path("myEndpoint"){
    get{
      complete(StatusCodes.OK)
    } /* VERY IMPORTANT ---> */ ~
    post {
      complete(StatusCodes.Forbidden)
    }
  } ~
    path("home"){
      complete(
        HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |  <body>
            |   Hello from high level akka HTTP
            |  </body>
            |</html>
            |""".stripMargin
        ))
    }

  Http().bindAndHandle(chainedRoutes,"localhost",8080)
}
