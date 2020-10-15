package com.noproject.service

import cats.implicits._
import cats.effect.IO
import com.noproject.common.data.gen.RandomValueGenerator
import com.noproject.common.domain.dao.{EventLogDao, DefaultPersistenceTest}
import com.noproject.common.domain.model.eventlog.{EventLogItem, EventLogObjectType}
import com.noproject.common.domain.service.EventLogDataService
import com.noproject.common.stream.{RabbitConfig, RabbitProducer, SimpleRabbitConfig, DefaultRabbitTest}
import com.noproject.common.domain.codec.DomainCodecs._
import dev.profunktor.fs2rabbit.interpreter.Fs2Rabbit
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class StatisticsServiceIntegrationTest extends WordSpec with DefaultPersistenceTest with DefaultRabbitTest with Matchers with BeforeAndAfterAll with RandomValueGenerator {

  val dao   = new EventLogDao(xar)
  val elds  = new EventLogDataService(dao)

  val conf = SimpleRabbitConfig(
    host     = "127.0.0.1"//String
  , port     = 5672//Int
  , user     = Some("rabbit")//Option[String]
  , password = Some("rabbit")//Option[String]
  )

  var fs2r: Fs2Rabbit[IO] = _
  val timer = IO.timer(ExecutionContext.global)

  override protected def afterAll(): Unit = {
    dao.deleteAll().unsafeRunSync()
    super.afterAll()
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val fs2rconf = RabbitConfig.buildConfig(conf)
    fs2r = Fs2Rabbit.apply[IO](fs2rconf).unsafeRunSync()
    dao.deleteAll().unsafeRunSync()
  }


  "StatisticsService" should {

    "post and read txn events" in {
      val postIO = immediateRabbitBridge[EventLogItem]("events", "events-q", "events-rk", fs2r).use {
        case (pub, sub) =>
          postEvents(2, EventLogObjectType.CashbackTxn, pub)
      }
      val io = for {
        posted  <- postIO
        persist <- dao.findByType(EventLogObjectType.CashbackTxn)
      } yield (posted, persist)
      val (post, pers) = io.unsafeRunSync
      val messages1 = post.map(_.message).toSet
      val messages2 = pers.map(_.message).toSet
      messages1 shouldEqual messages2
    }


//    TODO: no merchant events are handled atm.
//    "post and read offer events" in {
//      val postIO = immediateRabbitBridge[EventLogItem]("events", "events-q", "events-rk", fs2r).use {
//        case (pub, sub) =>
//          val post1 = postEvents(2, EventLogObjectType.Merchant, pub)
//          val post2 = postEvents(2, EventLogObjectType.Merchant, pub)
//          post1 <+> post2
//      }
//      val io = for {
//        posted  <- postIO
//        waited  <- timer.sleep(5 seconds)
//        persist <- dao.findByType(EventLogObjectType.Merchant)
//      } yield (posted, persist)
//      val (post, pers) = io.unsafeRunSync
//      val messages1 = post.map(_.message).toSet
//      val messages2 = pers.map(_.message).toSet
//      messages1 shouldEqual messages2
//    }

  }

  private def postEvents(count: Int, ofType: EventLogObjectType, pub: RabbitProducer[EventLogItem]): IO[List[EventLogItem]] = {
    val events = (0 until count).toList.map { _ => genEvent(ofType) }
    pub.submit(ofType.entryName, events) *> timer.sleep(1 second) *> IO.pure(events)
  }

  private def genEvent(ofType: EventLogObjectType): EventLogItem = {
    EventLogItem(None, randomInstant, ofType, randomOptString, None, "message:" + randomString, None)
  }

}
