import com.typesafe.config.{Config, ConfigFactory}
import com.warrenstrange.googleauth.GoogleAuthenticator
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class SingleLeaseMultipleChecks extends Simulation {
  val config: Config = ConfigFactory.load()

  val authenticator: GoogleAuthenticator = new GoogleAuthenticator()

  val requestJwt = exec(
      http("Lease token")
        .post("lease")
        .proxy(Proxy("proxyout.reform.hmcts.net", 8080))
        .body(StringBody("""{"microservice": """" + config.getString("service.name") + """", "oneTimePassword": "${otp}" }"""))
        .asJSON
        .check(bodyString.saveAs("jwt"))
  )

  val checkJwt = exec(
    http("Check token")
      .get("details")
      .proxy(Proxy("proxyout.reform.hmcts.net", 8080))
      .header("Authorization", "Bearer ${jwt}")
      .check(bodyString.is(config.getString("service.name")))
  )

  val otpFeeder = Iterator.continually(Map("otp" -> authenticator.getTotpPassword(config.getString("service.pass"))))

  setUp(
    scenario("Testing")
      .feed(otpFeeder)
      .exec(requestJwt)
      .during(2 minutes) {
        exec(checkJwt)
            .pause(40 seconds, 60 seconds)
      }
      .inject(
        rampUsers(1) over (1 minutes)
      )
  )
    .protocols(
      http
        .baseURL(config.getString("baseUrl"))
    )
}
