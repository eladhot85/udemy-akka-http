package part3_highlevelintro

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtSprayJson}
import spray.json._

import java.util.concurrent.TimeUnit
import scala.util.Success
import scala.util.Failure

/*
  github.com/pauldijou/jwt-scala
 */

object SecurityDomain extends DefaultJsonProtocol {
  case class LoginRequest(username: String, password: String)

  implicit val loginRequestFormat = jsonFormat2(LoginRequest)
}

object JwtAuthorization extends App with SprayJsonSupport {
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()

  import system.dispatcher

  import SecurityDomain._

  val superSecretPasswordDb = Map(
    "admin" -> "admin",
    "daniel" -> "Rockthejvm1!"
  )

  val algorithm = JwtAlgorithm.HS256
  val secretKey = "rockthejvmsecret"

  def checkPassword(username: String, password: String): Boolean = superSecretPasswordDb.contains(username) && superSecretPasswordDb(username) == password

  def createToken(username: String, experaionPeriodInDays: Int) = {
    val claims = JwtClaim(
      expiration = Some(System.currentTimeMillis() / 1000 + TimeUnit.DAYS.toSeconds(experaionPeriodInDays)),
      issuedAt = Some(System.currentTimeMillis() / 1000),
      issuer = Some("rockthejvm.com")
    )
    JwtSprayJson.encode(claims, secretKey, algorithm) // JWT string

  }

  def isTokenExpired(token: String): Boolean = JwtSprayJson.decode(token, secretKey, Seq(algorithm)) match {
    case Success(claims) =>
      val expiration = claims.expiration.getOrElse(0L)
      val currentTimeInSeconds = System.currentTimeMillis() / 1000
      expiration < currentTimeInSeconds
    case Failure(ex) =>
      println(ex)
      true
  }

  def isTokenValid(token: String): Boolean = JwtSprayJson.isValid(token, secretKey, Seq(algorithm))

  val loginRoute =
    post {
      entity(as[LoginRequest]) {
        case LoginRequest(username, password) if checkPassword(username, password) =>
          val token = createToken(username, 1)
          respondWithHeader(RawHeader("Access-Token", token)) {
            complete(StatusCodes.OK)
          }
        case _ => complete(StatusCodes.Unauthorized)
      }
    }


  val authenticatedRoute =
    (path("secureEndpoint") & get) {
      optionalHeaderValueByName("Authorization") {
        case Some(token) =>
          if (isTokenValid(token)) {
            if (isTokenExpired(token)) {
              complete(HttpResponse(status = StatusCodes.Unauthorized, entity = "Token expired"))
            } else {
              complete("User accessed authorized endpoint!")
            }
          } else {
            complete(HttpResponse(status = StatusCodes.Unauthorized, entity = "Token is invalid or has been tampered with"))
          }
        case _ => complete(HttpResponse(status = StatusCodes.Unauthorized, entity = "No Token provided"))
      }
    }

  val route = loginRoute ~ authenticatedRoute
  Http().bindAndHandle(route, "localhost", 8080)
  /*
   curl -i -XPOST -H "Content-Type: application/json" localhost:8080 --data "@src/main/json/login.json"
   curl -i localhost:8080/secureEndpoint -H "Authorization: eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJyb2NrdGhlanZtLmNvbSIsImV4cCI6MTY0ODExMjU0MCwiaWF0IjoxNjQ4MDI2MTQwfQ.31ZG0i3hs2CalkUvlueEuA5zPnC-rSvv6dCaw6R_6rs"

   */
}
