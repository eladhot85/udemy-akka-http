package part3_highlevelintro

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import spray.json._


case class Player(nickname: String, characterClass: String, level: Int)



object GameAreaMap {
  case object GetAllPlayers

  case class GetPlayer(nickname: String)

  case class GetPlayersByClass(characterClass: String)

  case class AddPlayer(player: Player)

  case class RemovePlayer(player: Player)

  case object OperationSuccess
}

class GameAreaMap extends Actor with ActorLogging {

  import part3_highlevelintro.GameAreaMap._

  var players = Map[String, Player]()

  override def receive: Receive = {
    case GetAllPlayers =>
      log.info("Getting All players")
      sender() ! players.values.toList

    case GetPlayer(nickname: String) =>
      log.info(s"Getting player by nickname $nickname")
      sender() ! players.get(nickname)

    case GetPlayersByClass(characterclass: String) =>
      log.info("Getting players by class")
      sender() ! players.values.toList.filter(_.characterClass == characterclass)

    case AddPlayer(player: Player) =>
      log.info(s"Trying to add player $player")
      players = players + (player.nickname -> player)
      sender() ! OperationSuccess

    case RemovePlayer(player: Player) =>
      log.info(s"Trying to remove player $player")
      players = players - player.nickname
      sender() ! OperationSuccess


  }
}


trait PlayerJsonProtocol extends DefaultJsonProtocol{
  implicit val playerFormat = jsonFormat3(Player)
}




object MarshalingJSON extends App with PlayerJsonProtocol with SprayJsonSupport{
  implicit val system = ActorSystem("MarshalingJSON")
  implicit val mat = ActorMaterializer()

  import system.dispatcher
  import part3_highlevelintro.GameAreaMap._


  val rtjvmGameMap = system.actorOf(Props[GameAreaMap], "AreaMap")
  val playerList = List(
    Player("martin_killz_u", "Warrior", 70),
    Player("rolandbraveheart007", "Elf", 67),
    Player("daniel_rock03", "Wizard", 30)
  )

  playerList.foreach { player =>
    rtjvmGameMap ! AddPlayer(player)
  }


  /**
   * - GET /api/player - return all playes in the map, as JSON
   * - GET /api/player/{nickname} - returns the player with the given nickname
   * - GET /api/player?nickname=X - returns the player with the given nickname
   * - GET /api/player/class/{charClass} - returns all the players with the given character class
   * - POST /api/player with JSON payload, adds the player from the map
   * - DELETE /api/player with JSON payload, removes the player from the map
   */


  implicit val timeout = Timeout(2 seconds)
  val rtjvmRouteSkel =
    pathPrefix("api" / "player") {
      get {
        path("class" / Segment) { characterClass =>
          //TODO 1: get all the players with characterClass
          val playersByClassFuture= (rtjvmGameMap ? GetPlayersByClass(characterClass)).mapTo[List[Player]]
          complete(playersByClassFuture)
        } ~
          (path(Segment) | parameter('nickname)) { nickname =>
            //TODO 2: get  the player with that nickname
            val playerOptionFuture= (rtjvmGameMap ? GetPlayer(nickname)).mapTo[Option[Player]]
            complete(playerOptionFuture)
          } ~
          pathEndOrSingleSlash {
            //TODO 3: get ALL players
            val playersFuture= (rtjvmGameMap ? GetAllPlayers).mapTo[List[Player]]
            complete(playersFuture)
          }

      } ~
        post {
            //TODO 4: add a player
          entity(as[Player]){ player =>
           complete((rtjvmGameMap ? AddPlayer(player)).map(_=> StatusCodes.OK))
          }
        } ~
        delete {
        //TODO 5: remove a player
          entity(as[Player]) { player =>
           complete((rtjvmGameMap ? RemovePlayer(player)).map(_ => StatusCodes.OK))
          }
        }
    }


  Http().bindAndHandle(rtjvmRouteSkel,"localhost",8080)

}
