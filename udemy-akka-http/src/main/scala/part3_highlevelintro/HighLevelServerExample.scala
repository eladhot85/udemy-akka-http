package part3_highlevelintro

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import part2_lowlevelserver.GuitarDB.CreateGuitar
import part2_lowlevelserver.{Guitar, GuitarDB, GuitarStoreJsonProtocol}

import scala.concurrent.Future
import scala.language.postfixOps
import spray.json._

object HighLevelServerExample extends App with GuitarStoreJsonProtocol {
  implicit val system = ActorSystem("HighLevelServerExample")
  implicit val mat = ActorMaterializer()

  import akka.http.scaladsl.server.Directives._
  import akka.pattern.ask
  import system.dispatcher
  import scala.concurrent.duration._

  /**
   * Exercise - write the guitar API from part2
   */
  /*
    GET /api/guitar  -- fetches All guitars in the store
    GET /api/guitar?id=x  -- fetches the guitar with id X
    GET /api/guitar/x  -- fetches the guitar with id x
    GET /api/guitar/inventory?inStock=true
  */


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

  // json support

  implicit val timeout = Timeout(2 seconds)

  import GuitarDB._

  val guitarServerRoute = {
    (path("api" / "guitar")) {
      (parameter('id.as[Int])) { guitarId =>
        get {
          val guitarFuture: Future[Option[Guitar]] = (guitarDB ? FindGuitar(guitarId)).mapTo[Option[Guitar]]
          val entityFuture = guitarFuture.map(guitarOpt =>
            HttpEntity(
              ContentTypes.`application/json`,
              guitarOpt.toJson.prettyPrint
            ))
          complete(entityFuture)
        }
      } ~
      get {
        val guitarsFuture: Future[List[Guitar]] = (guitarDB ? FindAllGuitars).mapTo[List[Guitar]]
        val entityFuture = guitarsFuture.map(guitars =>
          HttpEntity(
            ContentTypes.`application/json`,
            guitars.toJson.prettyPrint
          ))

        complete(entityFuture)
      }

    } ~
      path("api" / "guitar" / IntNumber) { guitarId =>
        get {
          val guitarFuture: Future[Option[Guitar]] = (guitarDB ? FindGuitar(guitarId)).mapTo[Option[Guitar]]
          val entityFuture = guitarFuture.map(guitarOpt =>
            HttpEntity(
              ContentTypes.`application/json`,
              guitarOpt.toJson.prettyPrint
            ))
          complete(entityFuture)
        }
      } ~
      path("api" / "guitar" / "inventory") {
        get {
          parameter('inStock.as[Boolean]) { inStock =>
            val guitarsFuture: Future[List[Guitar]] = (guitarDB ? FindGuitarsInStock(inStock)).mapTo[List[Guitar]]
            val entityFuture = guitarsFuture.map(guitars =>
              HttpEntity(
                ContentTypes.`application/json`,
                guitars.toJson.prettyPrint
              ))
            complete(entityFuture)
          }
        }
      }

  }


  def toHttpEntity(payload: String) = HttpEntity(ContentTypes.`application/json`, payload)

val simplifiedGuitarServerRoute =
  (pathPrefix("api" / "guitar") & get) {
    path("inventory") {
      parameter('inStock.as[Boolean]) { inStock =>
        complete(
          (guitarDB ? FindGuitarsInStock(inStock)).mapTo[List[Guitar]]
          .map(_.toJson.prettyPrint)
          .map(toHttpEntity))
      }
    } ~
      (path(IntNumber) | parameter('id.as[Int])) { guitarId =>
        complete(
          (guitarDB ? FindGuitar(guitarId)).mapTo[Option[Guitar]]
          .map(_.toJson.prettyPrint)
          .map(toHttpEntity))
      } ~
      pathEndOrSingleSlash {
        complete(
          (guitarDB ? FindAllGuitars).mapTo[List[Guitar]]
            .map(_.toJson.prettyPrint)
            .map(toHttpEntity))
      }
  }



  Http().bindAndHandle(simplifiedGuitarServerRoute, "localhost", 8080)
}
