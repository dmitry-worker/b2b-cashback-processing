package com.noproject.controller.route

import cats.effect.IO
import com.noproject.common.codec.csv.{CsvCodecs, CsvEscapeOption, CsvFormat, CsvSeparatorOption}
import com.noproject.common.controller.dto.TransactionSearchParams
import com.noproject.common.controller.route.MonitoredRouting
import com.noproject.common.domain.model.transaction.CashbackTransactionResponse
import com.noproject.common.domain.service.CashbackTransactionDataService
import com.noproject.common.service.auth.Authenticator
import com.noproject.domain.model.customer.CustomerSession
import io.circe.generic.auto._
import javax.inject.{Inject, Named, Singleton}
import org.http4s.Charset.`UTF-8`
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.headers._
import org.http4s.{EntityDecoder, EntityEncoder, MediaType, Request}

@Singleton
class TransactionRoute @Inject()(
  ds: CashbackTransactionDataService
, @Named("customer")
  authenticator: Authenticator[CustomerSession]
) extends AuthenticatedRouting(authenticator) with MonitoredRouting with CsvCodecs {

  import com.noproject.common.codec.json.ElementaryCodecs._

  override val monitoringPrefix = "txns"

  implicit val mercEncoder: EntityEncoder[IO, CashbackTransactionResponse] = jsonEncoderOf[IO, CashbackTransactionResponse]
  implicit val mercSeqEncoder: EntityEncoder[IO, Seq[CashbackTransactionResponse]] = jsonEncoderOf[IO, Seq[CashbackTransactionResponse]]
  implicit val tspDecoder: EntityDecoder[IO, TransactionSearchParams] = jsonOf[IO, TransactionSearchParams]

  private val txnApiPath = baseApiPath / "v1" / "transactions"
  private val defaultLimit = 10

  private def normalize(tsp: TransactionSearchParams): TransactionSearchParams = {
    tsp.copy(
      limit = tsp.limit.orElse(Some(defaultLimit)),
      network = tsp.network.map(_.toLowerCase)
    )
  }

  "Search transactions" **
  POST / txnApiPath >>> Auth.auth |>> { (req: Request[IO], session: CustomerSession) =>
    redeemAndSuccess {
      req.as[TransactionSearchParams].flatMap { tsp =>
        logger.info(s"POST search transactions with request $tsp for user ${session.customerName}")
        ds.find(normalize(tsp), session.customerName).map(list => list.map(CashbackTransactionResponse(_)))
      }
    }
  }

  "Get transaction by identifier" **
  GET / txnApiPath / pathVar[String]("transactionId") >>> Auth.auth |>> { (transactionId: String, session: CustomerSession) =>
    redeemAndSuccess {
      logger.info(s"GET transaction by id $transactionId for user ${session.customerName}")
      ds.findById(transactionId, session.customerName).map(CashbackTransactionResponse(_))
    }
  }


  private val fmt     = CsvFormat(CsvSeparatorOption.Semicolon, CsvEscapeOption.All)
  private val codec   = CsvCodecs.apply[CashbackTransactionResponse]
  private val writer  = codec.apply(fmt)

  "Post transaction params and receive transactions as a csv stream" **
  POST / txnApiPath / "stream" >>> Auth.auth |>> { (req: Request[IO], session: CustomerSession) =>
    redeemAndCsv {
      searchTransactions(req, session)
    }
  }

  /** Search transactions and return CSV content. */
  def searchTransactions( req: Request[IO], session: CustomerSession ): IO[fs2.Stream[IO, String]] =
  {
    req.as[TransactionSearchParams].flatMap { tsp =>
      IO.delay(fs2.Stream(writer.header) ++ ds.stream(tsp, session.customerName).map { txn =>
        writer.apply(CashbackTransactionResponse(txn))
      })
    }
  }

}
