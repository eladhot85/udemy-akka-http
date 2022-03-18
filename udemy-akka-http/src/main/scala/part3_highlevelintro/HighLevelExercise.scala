package part3_highlevelintro

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest, HttpResponse, StatusCodes}
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import part3_highlevelintro.HighLevelExercise.Person
import spray.json._

import scala.concurrent.duration._
import scala.util.{Failure, Success}


trait PersonJsonProtocol extends DefaultJsonProtocol {
  implicit val personFormat = jsonFormat2(Person)
}

object HighLevelExercise extends App with PersonJsonProtocol {

  implicit val system = ActorSystem("HighLevelExercise")
  implicit val mat = ActorMaterializer()

  import system.dispatcher

  /**
   * Exercise:
   * - GET /api/people : retrieve all people registered
   * - GET /api/people/{pin} retrieve the person with that PIN, return as JSON
   * - GET /api/people?pin=X retrieve the person with that PIN, return as JSON
   * - POST /api/people with a JSON payload denoting a Person - add that person to DB
   * - extract the HTTP request's payload
   * - process the entity's data
   *
   *
   * */
  case class Person(pin: Int, name: String)


  var people = List(
    Person(1, "Alice"),
    Person(2, "Bob"),
    Person(3, "Charlie")
  )


  def completeWithJsonStr(str: String): Route = {
    complete {
      HttpEntity(
        ContentTypes.`application/json`,
        str
      )
    }
  }

  def completeWithNotFound(): Route = {
    complete {
      StatusCodes.NotFound
    }
  }


  def findPersonByPin(pin: Int): Route = {
    people.find(p => p.pin == pin)
      .map(_.toJson.prettyPrint)
      .map(completeWithJsonStr)
      .getOrElse(completeWithNotFound())
  }

  def fetchAllPersons(): Route = {
    val str = people
      .toJson.prettyPrint
    completeWithJsonStr(str)
  }


  def createPerson(p: Person): Unit = {
    people = people :+ p
  }


  val peopleRoute =
    (pathPrefix("api" / "people")) {
      get {
        (path(IntNumber) | parameter('pin.as[Int])) { pin =>
          findPersonByPin(pin)
        } ~
          pathEndOrSingleSlash {
            fetchAllPersons()
          }
      } ~
        (post & pathEndOrSingleSlash & extractRequest & extractLog) { (request, log) =>
          val strictEntityFuture = request.entity.toStrict(3 seconds)
          val personFuture = strictEntityFuture.map { strictEntity =>
            val jsonString = strictEntity.data.utf8String
            val person = jsonString.parseJson.convertTo[Person]
            person
          }
          onComplete(personFuture) {
            case Success(person) =>
              log.info("Got person " + person)
              createPerson(person)
              complete(personFuture.map(_ => StatusCodes.OK))
            case Failure(ex) =>
              log.warning("Something failed with fetching person from the entity:" + ex)
              failWith(ex)
          }
        }
    }
  Http().bindAndHandle(peopleRoute, "localhost", 8080)


}
