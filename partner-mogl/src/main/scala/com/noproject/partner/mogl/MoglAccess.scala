package com.noproject.partner.mogl

import cats.data.StateT
import cats.effect._
import com.noproject.common.logging.DefaultLogging
import com.noproject.partner.mogl.config.MoglConfig
import com.noproject.partner.mogl.model.{MoglAccessToken, MoglUserInfoResponse}
import io.circe.generic.auto._
import org.http4s.circe._
import org.http4s.client.{Client, UnexpectedStatus}
import org.http4s.headers.Accept
import org.http4s.{EntityDecoder, Header, Headers, MediaType, Request, Uri}

case class MoglAccess( httpClient : Client[IO], config: MoglConfig ) extends DefaultLogging {

  var oauthRequest : Request[IO] = Request[IO](
      uri = Uri() .withPath( "https://" + config.api.host + "/oauth/token" )
        .withQueryParam( "grant_type", "client_credentials")
        .withQueryParam( "client_id", config.api.key )
        .withQueryParam( "client_secret", config.api.secret ),
      headers = Headers.of( Accept( MediaType.application.json ) ) )

  implicit val userInfoDecoder : EntityDecoder[IO, MoglUserInfoResponse] = jsonOf[IO, MoglUserInfoResponse]
  implicit val accessTokenDecoder : EntityDecoder[IO, MoglAccessToken] = jsonOf[IO, MoglAccessToken]

  case class MoglState( token : MoglAccessToken )

  type Mogl[A] = StateT[ IO, MoglState, A ]

  private def getAccessTokenIO( moglConfig: MoglConfig ) : IO[ MoglAccessToken ] = {
    httpClient.expect[MoglAccessToken]( oauthRequest )
  }

  def runMogl[A]( op : Mogl[A] ) : IO[A] = {
    for {
      accessToken <- getAccessTokenIO( config )
      result <- op.run( MoglState( accessToken ) )
    } yield result._2
  }

  private def refreshToken : Mogl[Unit] = {
    StateT.apply( (st : MoglState) => for {
      accessToken <- getAccessTokenIO( config )
    } yield (MoglState( accessToken ) , Unit) )
  }

  def doMoglRequest[A]( request : Request[IO] )(implicit d: EntityDecoder[IO,A]) : Mogl[ Either[ Throwable, A ] ] = {
    for {
      r1 <- doMoglRequestOnce[A]( request )
      r2 <- r1 match {
          case Left( t ) => for {
            _ <- refreshToken
            resp <- doMoglRequestOnce[A](request)
          } yield resp
          case Right( _ ) => StateT.pure( r1 ) : Mogl[ Either[Throwable, A ]]
        }
    } yield r2
  }

  private def doMoglRequestOnce[ A ]( req: Request[IO] )(implicit d: EntityDecoder[IO, A]) : Mogl[ Either[ Throwable, A ] ] =
    StateT.inspectF( (st : MoglState) => doMoglRequestIO[A]( st.token, req ) )


  private def doMoglRequestIO[A]( accessToken : MoglAccessToken, req : Request[IO] )(implicit d: EntityDecoder[IO, A]) : IO[Either[Throwable, A] ] = {

    val req2 = req.putHeaders( Header( "Authorization", "Bearer " + accessToken.access_token ) )
    httpClient.expect[A](req2).redeem( e => Left( e ), Right.apply )
  }

  def getUserInfo( moglUserId : String ): Mogl[ MoglUserInfoResponse ] = {

    val request = Request[IO]( uri = Uri( )
        .withPath("https://" + config.api.host + "/api/v2/users/" + moglUserId)
        .withQueryParam( "client_id", config.api.key) )

    doMoglRequest[ MoglUserInfoResponse ]( request ).map
      {
        case Left( non200Status: UnexpectedStatus ) => throw non200Status
        //todo: Some kind of error logging needed here for human intervention. Prometheus?
        case Left( anyOther ) => /*logic*/ throw anyOther
        case Right( result ) => result
      }
  }

}

