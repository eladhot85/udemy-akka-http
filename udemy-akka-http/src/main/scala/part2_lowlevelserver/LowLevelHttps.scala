package part2_lowlevelserver

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import akka.stream.ActorMaterializer

import java.io.InputStream
import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

object HttpsContext{
  //Step1: key store
  val ks : KeyStore = KeyStore.getInstance("PKCS12")
  val keystoreFile : InputStream = getClass.getClassLoader.getResourceAsStream("keystore.pkcs12")
  val password = "akka-https".toCharArray // fetch the password from secure place
  ks.load(keystoreFile,password)

  //Step2: key manager

  val keyManagerFactory = KeyManagerFactory.getInstance("SunX509") // PKI = public key infrastructure
  keyManagerFactory.init(ks,password)

  //Step 3: trust manager
  val trustManagerFactory = TrustManagerFactory.getInstance("SunX509")
  trustManagerFactory.init(ks)

  //Step 4: initialize an SSL context
  val sslContext : SSLContext = SSLContext.getInstance("TLS")
  sslContext.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers,new SecureRandom)

  //Step 5: return the https connection context
  val httpConnectionContext : HttpsConnectionContext = ConnectionContext.https(sslContext)
}


object LowLevelHttps extends App {
  implicit val system = ActorSystem("LowLevelHttps")
  implicit val mat = ActorMaterializer()




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

  val httpsBinding = Http().bindAndHandleSync(requestHandler,"localhost", 8443,HttpsContext.httpConnectionContext)



}
