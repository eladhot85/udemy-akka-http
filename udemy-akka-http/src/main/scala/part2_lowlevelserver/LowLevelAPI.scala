package part2_lowlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.IncomingConnection
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink}

import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.concurrent.duration._

object LowLevelAPI extends App {
  implicit val system = ActorSystem("LowLevelAPI")
  implicit val mat = ActorMaterializer()
  import system.dispatcher


  val serverSource = Http().bind("localhost",8000)
  val connectionSink = Sink.foreach[IncomingConnection] { connection =>
    println(s"Accepted incoming connection from ${connection.remoteAddress}")

  }

  val serverBindingFuture = serverSource.to(connectionSink).run()
  serverBindingFuture.onComplete{
    case Success(binding) => println("server binding successful")
    binding.terminate(2 seconds)
    case Failure(exception) => println(s"Server binding failed : $exception")
  }

 /*
  Method 1: synchronously serve HTTP responses
  */

  val requestHandler : HttpRequest => HttpResponse = {
    case HttpRequest(HttpMethods.GET, _, _, _, _) => HttpResponse(
     status =  StatusCodes.OK, //HTTP 200
     entity = HttpEntity(
       ContentTypes.`text/html(UTF-8)`,
       """
         |<html>
         |<body>
         |Hello from Akka Http!
         |</body>
         |</html>
         |""".stripMargin
     )
    )
    case req: HttpRequest => req.discardEntityBytes()
    HttpResponse(
      StatusCodes.NotFound, //404
      entity = HttpEntity(
        """
          |<html>
          |<body>
          |OOPS! The resource not found
          |</body>
          |</html>
          |""".stripMargin
      )
    )
  }

  val httpSyncConnectionHandler =Sink.foreach[IncomingConnection]{connection =>
    connection.handleWithSyncHandler(requestHandler)
  }

//  Http().bind("localhost",8080).runWith(httpSyncConnectionHandler)
  //short version
//  Http().bindAndHandleSync(requestHandler,"localhost",8080)



  /*
  Method 2: server back HTTP responses ASYNCHRONOUSLY
   */
  val asyncRequestHandler : HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.GET, Uri.Path("/home"), _, _, _) =>
      Future(HttpResponse(
      status =  StatusCodes.OK, //HTTP 200
      entity = HttpEntity(
        ContentTypes.`text/html(UTF-8)`,
        """
          |<html>
          |<body>
          |Hello from Akka Http!
          |</body>
          |</html>
          |""".stripMargin
      )
    ))
    case req: HttpRequest => req.discardEntityBytes()
     Future( HttpResponse(
        StatusCodes.NotFound, //404
        entity = HttpEntity(
          """
            |<html>
            |<body>
            |OOPS! The resource not found
            |</body>
            |</html>
            |""".stripMargin
        )
      ))
  }

  Http().bindAndHandleAsync(asyncRequestHandler,"localhost",8081)


  //Method 3 : via Akka Streams
  val streamBasedRequestHandler : Flow[HttpRequest,HttpResponse,_] = Flow[HttpRequest].map{
    case HttpRequest(HttpMethods.GET, Uri.Path("/home2"), _, _, _) =>
      HttpResponse(
        status =  StatusCodes.OK, //HTTP 200
        entity = HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            |<body>
            |Hello from Akka Http!
            |</body>
            |</html>
            |""".stripMargin
        )
      )
    case req: HttpRequest => req.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound, //404
        entity = HttpEntity(
          """
            |<html>
            |<body>
            |OOPS! The resource not found
            |</body>
            |</html>
            |""".stripMargin
        )
      )
  }

//  Http().bind("localhost",8082).runForeach{connection =>
//    connection.handleWith(streamBasedRequestHandler)
//  }
  //shor version
  Http().bindAndHandle(streamBasedRequestHandler,"localhost",8082)

  /**
   * Exercise : create your own HTTP server running on localhost on 8388, which replies
   * - with a welcome message on the "front door" localhost:8388
   * - with a proper HTML on localhost:8388/about
   * -with a 404 message otherwise
   */


  val flowHandler : Flow[HttpRequest,HttpResponse,_] = Flow[HttpRequest].map{
    case HttpRequest(HttpMethods.GET, Uri.Path("/"), _, _, _) => HttpResponse(StatusCodes.OK,entity = HttpEntity("welcome"))
    case HttpRequest(HttpMethods.GET, Uri.Path("/about"), _, _, _) => HttpResponse(StatusCodes.OK,entity = HttpEntity(
      ContentTypes.`text/html(UTF-8)`,
      """
        |<html>
        |<body>
        |Welcome to 'about' page !
        |</body>
        |</html>
        |""".stripMargin
    ))

    // path /search redirects to some other part of website
    case HttpRequest(HttpMethods.GET, Uri.Path("/search"), _, _, _) =>
      HttpResponse(
        StatusCodes.Found,
        headers = List(Location("http://google.com"))
      )
    case request=>
      request.discardEntityBytes()
      HttpResponse(StatusCodes.NotFound,entity = HttpEntity(ContentTypes.`text/html(UTF-8)` ,
      """
        |<html>
        |<body>
        |OOPS! The resource not found
        |</body>
        |</html>
        |""".stripMargin

    ))

  }

 val bindingFuture =  Http().bindAndHandle(flowHandler,"localhost",8388)
 //shutdown the server:
  bindingFuture.flatMap(binding => binding.unbind())
    .onComplete(_=> system.terminate())

}
