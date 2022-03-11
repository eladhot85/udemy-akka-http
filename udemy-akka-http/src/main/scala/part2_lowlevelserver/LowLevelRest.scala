package part2_lowlevelserver

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.stream.ActorMaterializer
import part2_lowlevelserver.GuitarDB.{AddQuantity, CreateGuitar, FindAllGuitars, FindGuitarsInStock, GuitarCreated}
import spray.json._
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._

case class Guitar(make: String, model: String, quantity: Int = 0)

object GuitarDB {
  case class CreateGuitar(guitar: Guitar)
  case class GuitarCreated(id: Int)
  case class FindGuitar(id: Int)
  case object FindAllGuitars
  case class AddQuantity(id: Int, quantity: Int)
  case class FindGuitarsInStock(inStock: Boolean)
}

class GuitarDB extends Actor with ActorLogging {

  import GuitarDB._

  var guitars: Map[Int, Guitar] = Map()
  var currentGuitarId: Int = 0

  override def receive = {
    case FindAllGuitars =>
      log.info("Searching for all guitars")
      sender() ! guitars.values.toList

    case FindGuitar(id) =>
      log.info(s"Searching Guitar by id $id")
      sender() ! guitars.get(id)
    case CreateGuitar(guitar) =>
      log.info(s"Adding guitar $guitar with id $currentGuitarId")
      guitars = guitars + (currentGuitarId -> guitar)
      sender() ! GuitarCreated(currentGuitarId)
      currentGuitarId += 1
    case  AddQuantity(id, quantity) =>
      log.info(s"Trying to add $quantity items for guitar $id")
      val guitarOpt = guitars.get(id)
      val newGuitarOpt = guitarOpt.map{
        case Guitar(make,model,q) => Guitar(make,model, q+ quantity)
      }
      newGuitarOpt.foreach(guitar=> guitars = guitars + (id -> guitar))
      sender ! newGuitarOpt

    case FindGuitarsInStock(inStock) =>
      log.info(s"Searching for guitars ${if(inStock) "in" else "out of"} stock")
      if (inStock) {
        sender() ! guitars.values.filter(_.quantity > 0)
      }
      else {
        sender() ! guitars.values.filter(_.quantity == 0)
      }
  }
}

trait GuitarStoreJsonProtocol extends DefaultJsonProtocol {
  implicit val guitarFormat = jsonFormat3(Guitar)
}


object LowLevelRest extends App with GuitarStoreJsonProtocol {
  implicit val system = ActorSystem("LowLevelRest")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  /*
    GET on localhost:8080/api/guitar => ALL the guitars in the store
    POST on localhost:8080/api/guitar => Insert the guitar into the store
   */

  //JSON -> marshalling
  val simpleGuitar = Guitar("Fender", "Stratocaster")
  println(simpleGuitar.toJson.prettyPrint)

  //unmarshaling
  val simpleGuitarJsonStr =
    """
      |{
      |  "make": "Fender",
      |  "model": "Stratocaster",
      |  "quantity" : 3
      |}
      |""".stripMargin

  println(simpleGuitarJsonStr.parseJson.convertTo[Guitar])


  /*
    setup
   */
  val guitarDB = system.actorOf(Props[GuitarDB], "LowLevelGuitarDB")
  val guitarList = List(
    Guitar("Fender", "Stratocaster"),
    Guitar("Gibson", "Les Paul"),
    Guitar("Martin", "LX1")
  )

  guitarList.foreach(guitar =>
    guitarDB ! CreateGuitar(guitar)
  )

  /*
  server code
   */


  implicit val defaultTimeout = Timeout(2 seconds)
  val requestHandler: HttpRequest => Future[HttpResponse] = {
    case HttpRequest(HttpMethods.POST,uri@Uri.Path("/api/guitar/inventory"),_,_,_)=>
      val query = uri.query()
      val guitarId : Option[Int] = query.get("id").map(_.toInt)
      val guitarQuantity : Option[Int] = query.get("quantity").map(_.toInt)

      val validGuitarResponseFuture :Option[Future[HttpResponse]] = for {
        id <- guitarId
        quantity <- guitarQuantity
      } yield {
        val newGuitarFuture = (guitarDB ? AddQuantity(id,quantity)).mapTo[Option[Guitar]]
        newGuitarFuture.map(_ => HttpResponse(StatusCodes.OK))
      }

      validGuitarResponseFuture.getOrElse(Future(HttpResponse(StatusCodes.BadRequest)))

    case HttpRequest(HttpMethods.GET,uri@Uri.Path("/api/guitar/inventory"),_,_,_)=>
      val query = uri.query()
      val inStockOpt : Option[Boolean] = query.get("inStock").map(_.toBoolean)
      inStockOpt match {
        case Some(inStock) =>
          val guitarsFuture: Future[List[Guitar]] = (guitarDB ? FindGuitarsInStock(inStock)).mapTo[List[Guitar]]
          guitarsFuture.map { guitars =>
           HttpResponse(
             entity = HttpEntity(
               ContentTypes.`application/json`,
               guitars.toJson.prettyPrint)
            )
          }

        case None => Future(HttpResponse(StatusCodes.BadRequest))
      }




    case HttpRequest(HttpMethods.GET, Uri.Path("/api/guitar"), _, _, _) =>
      val guitarFuture = (guitarDB ? FindAllGuitars).mapTo[List[Guitar]]
      guitarFuture.map { guitars =>
        HttpResponse(
          entity = HttpEntity(
            ContentTypes.`application/json`,
            guitars.toJson.prettyPrint
          )
        )
      }
    case HttpRequest(HttpMethods.POST, Uri.Path("/api/guitar"), _, entity, _) =>
      //entities are Source[ByteString]
      val strictEntityFuture = entity.toStrict(3 seconds)
      strictEntityFuture.flatMap { strictEntity =>
        val guitarJsonString = strictEntity.data.utf8String
        val guitar = guitarJsonString.parseJson.convertTo[Guitar]
        val guitarCreatedFuture: Future[GuitarCreated] = (guitarDB ? CreateGuitar(guitar)).mapTo[GuitarCreated]
        guitarCreatedFuture.map { _ =>
          HttpResponse(StatusCodes.OK)
        }
      }


    case request: HttpRequest =>
      request.discardEntityBytes()
      Future {
        HttpResponse(status = StatusCodes.NotFound)
      }

  }

  Http().bindAndHandleAsync(requestHandler, "localhost", 8080)
  // curl -i http://localhost:8080/api/guitar -X POST --data "@guitar.json"
}
