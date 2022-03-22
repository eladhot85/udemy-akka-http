package part3_highlevelintro

import akka.actor.ActorSystem
import akka.http.javadsl.server.MissingQueryParamRejection
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{MethodRejection, Rejection, RejectionHandler}

object HandlingRejections extends App {
  implicit val system = ActorSystem("HandlingRejections")
  implicit val mat = ActorMaterializer()
  import system.dispatcher


  val simpleRoute =
    path("api"/ "myEndpoint"){
      get{
        complete(StatusCodes.OK)
      } ~
      parameter('id){ _ =>
        complete(StatusCodes.OK)
      }
    }

  // Rejection handlers
  val badRequestHandler : RejectionHandler = {rejections: Seq[Rejection] =>
    println(s"I have encountered rejections: $rejections" )
    Some(complete(StatusCodes.BadRequest))
  }

  val forbiddenRequestHandler : RejectionHandler = {rejections: Seq[Rejection] =>
    println(s"I have encountered rejections: $rejections" )
    Some(complete(StatusCodes.Forbidden))
  }

  val simpleRouteWithHandlers =
    handleRejections(badRequestHandler){ //handle rejections from the top level
      path("api"/ "myendpoint") {
        get {
          complete(StatusCodes.OK)
        } ~
          post{
            handleRejections(forbiddenRequestHandler){ //handle rejection WITHIN
              parameter('myParam){ _ =>
                complete(StatusCodes.OK)
              }
            }
          }
      }
    }

//  Http().bindAndHandle(simpleRouteWithHandlers, "localhost",8080)

      //list(method rejection, query rejection)
  implicit val customRejectionHandler = RejectionHandler.newBuilder()
    .handle{
      case m: MethodRejection =>
        println(s"I got a method rejection: $m")
        complete("Rejected Method!")
    }.handle {
    case m: MissingQueryParamRejection =>
      println(s"I got a param rejection: $m")
      complete("Rejected query param!")
    }.result()

  //sealing a route
  //now the simpleRoute will work with the implicit Handler
  Http().bindAndHandle(simpleRoute, "localhost",8080)
}
