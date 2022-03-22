package part3_highlevelintro

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.ExceptionHandler


object HandlingExceptions extends App{
  implicit val system = ActorSystem("HandlingExceptions")
  implicit val mat = ActorMaterializer()
  import system.dispatcher


  val simpleRoute =
    path("api"/ "people"){
      get{
        //directive that throws some exceptions
        throw new RuntimeException("Getting all the people took too long")
      }~
        post {
          parameter('id){id =>
            if(id.length > 2)
              throw new NoSuchElementException("Parameter $id cannot be found it the database")

            complete(StatusCodes.OK)
          }
        }
    }

  implicit val customExceptionHandler: ExceptionHandler = ExceptionHandler{
    case e: RuntimeException =>
      complete(StatusCodes.NotFound, e.getMessage)
    case e: IllegalArgumentException =>
      complete(StatusCodes.BadRequest, e.getMessage)
  }


//  Http().bindAndHandle(simpleRoute,"localhost", 8080)

  val runtimeExceptionHandler = ExceptionHandler{
    case e: RuntimeException =>
      complete(StatusCodes.NotFound, e.getMessage)
  }

  val noSuchElementExceptionHandler = ExceptionHandler{
    case e: NoSuchElementException =>
      complete(StatusCodes.NotFound, e.getMessage)
  }

  val delicateHandleRoute = {
    handleExceptions(runtimeExceptionHandler) {
      path("api" / "people") {
        get {
          //directive that throws some exceptions
          throw new RuntimeException("Getting all the people took too long")
        } ~
          handleExceptions(noSuchElementExceptionHandler) {
            post {
              parameter('id) { id =>
                if (id.length > 2)
                  throw new NoSuchElementException(s"Parameter $id cannot be found it the database")

                complete(StatusCodes.OK)
              }
            }
          }
      }
    }
  }


    Http().bindAndHandle(delicateHandleRoute,"localhost", 8080)
}
