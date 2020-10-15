package com.noproject.controller.route

import java.time.{Clock, Instant}

import cats.effect.IO
import com.noproject.common.config.{ConfigProvider, FailFastConfigProvider}
import com.noproject.common.data.gen.{Generator, RandomValueGenerator}
import com.noproject.common.domain.dao.DefaultPersistenceTest
import com.noproject.common.domain.dao.customer.CustomerDAO
import com.noproject.common.domain.model.customer.{AccessRole, Customer, CustomerUtil}
import com.noproject.common.domain.service.CustomerDataService
import com.noproject.config.AuthConfig
import com.noproject.controller.dto.auth.TokenRequest
import com.noproject.controller.route.admin.AdminCustomerRoute
import com.noproject.domain.dao.customer.{CustomerSessionDAO, SessionDAO}
import com.noproject.domain.model.customer.{CustomerSession, Session}
import com.noproject.domain.service.customer.SessionDataService
import com.noproject.service.auth.{AuthenticatorAdmin, AuthenticatorJWT, TokenService}
import org.http4s.{Header, Headers, HttpVersion, Method, Request, Uri}
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

// TODO: make tests with invalid params (hash, key, ...)
class AdminCustomerRouteTest extends WordSpec with BeforeAndAfterAll with DefaultPersistenceTest with RandomValueGenerator with Matchers with MockFactory {

  val genSecret   = Generator[String].apply
  val genCustomer = Generator[Customer].apply
  val validHash   = CustomerUtil.calculateHash(genCustomer.name, genCustomer.apiKey, genSecret).unsafeRunSync()
  val genSession  = Generator[Session].apply

  val roles: Set[AccessRole] = Set( AccessRole.Admin )
  val customer = genCustomer.copy(hash = validHash, role = roles)
  val session  = CustomerSession(1L, Some(customer.name), randomStringUUID, Instant.now, Instant.now.plusMillis(10000), roles)

  val authConfig = AuthConfig("testsession", 2, 0 )

  private object StaticConfigProvider extends ConfigProvider[AuthConfig] with FailFastConfigProvider[AuthConfig] {
    override protected def load: IO[AuthConfig] = IO.pure(authConfig)
  }

  val sessionDataService = stub[SessionDataService] //new SessionDataService( csdao, StaticConfigProvider )
  (sessionDataService.insert _).when(*).returns(IO.pure(session)).anyNumberOfTimes()
  (sessionDataService.getUnexpired _).when(*).returns(IO.pure(Some(session))).anyNumberOfTimes()
  val customerDataService = stub[CustomerDataService] //new CustomerDataService( cdao, sdao, xar, Clock.systemUTC() )
  (customerDataService.getByKey _).when(*).returns(IO.pure(customer)).anyNumberOfTimes()

  val tokenService = new TokenService( sessionDataService,  customerDataService, StaticConfigProvider )
  val authenticator = new AuthenticatorAdmin(StaticConfigProvider, sessionDataService)

  // Need a concrete implementation
  val authenticatedRouting = new AdminCustomerRoute( customerDataService, authenticator)


  "AdminCustomerRoute" should {
    "accept authorization given by token service" in {
      val session = genSession.copy( customerName = Some( customer.name ) )

      // Obtain the token that the user would be using.
      val (token, expires) = tokenService.tokenize( TokenRequest( customer.name, customer.apiKey, genSecret ) )
        .unsafeRunSync()

      val uri = Uri.fromString( "http://localhost/api/admin/v1/customer")
      val req = Request[IO]( Method.GET, uri.right.get, HttpVersion.`HTTP/1.1`,
        Headers.of( Header( "Authorization", token.toEncodedString ) ))

      val auth = authenticatedRouting.authUser( req )
        .unsafeRunSync()
      if( auth.isLeft )
        throw new RuntimeException( "Authentication token did not match: " + auth.left.get )

      auth.right.get.customerName shouldEqual session.customerName
    }
  }

}
