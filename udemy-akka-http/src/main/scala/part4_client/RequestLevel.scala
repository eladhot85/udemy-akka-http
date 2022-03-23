package part4_client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import spray.json._

import scala.util.{Failure, Success}

object RequestLevel extends App with PaymentJsonProtocol {
  implicit val system = ActorSystem("RequestLevel")
  implicit val mat = ActorMaterializer()
  import system.dispatcher

  val responseFuture = Http().singleRequest(HttpRequest(uri = "http://www.google.com"))
  responseFuture.onComplete{
    case Success(response) =>
      response.discardEntityBytes()
      println(s"The request was successful and retured: $response")
    case Failure(ex) =>
      println(s"The request failed with : $ex")

  }

  /*
  payment system
   */

  import PaymentSystemDomain._
  val creditCards = List(
    CreditCard("4242-4242-4242-4242","424","tx-test-account"),
    CreditCard("1234-1234-1234-1234","123","tx-daniel-account"),
    CreditCard("1234-1234-4321-4321","321","tx-my-awesome-account"),
  )


  val paymentRequests = creditCards.map(creditCard => PaymentRequest(creditCard, "rtjvm-store-account",99))
  val serverHttpRequests = paymentRequests.map(paymentRequest =>
    HttpRequest(
      HttpMethods.POST,
      uri = "http://localhost:8080/api/payments",
      entity = HttpEntity(
        ContentTypes.`application/json`,
        paymentRequest.toJson.prettyPrint
      )
    )
  )
  Source(serverHttpRequests)
    .mapAsync(10)(request=> Http().singleRequest(request)) //or mapAsyncUnordered
    .runForeach(println)


  //used for low volume + low latency requests
}
