package com.noproject.service

import java.time.Instant

import cats.effect.IO
import com.noproject.common.config.{ConfigProvider, FailFastConfigProvider, OffersConfig}
import com.noproject.common.data.gen.{Generator, RandomValueGenerator, TestClock}
import com.noproject.common.domain.dao.DefaultPersistenceTest
import com.noproject.common.domain.model.customer.Consumer
import com.noproject.common.domain.model.merchant.MerchantRow
import com.noproject.common.domain.service.{ConsumerDataService, MerchantCacheService}
import com.noproject.common.stream.impl.ConsumerQueue
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

import scala.util.{Failure, Success}

class TrackingServiceTest extends WordSpec with BeforeAndAfterAll with DefaultPersistenceTest with RandomValueGenerator with Matchers with MockFactory {

  object StaticOffersConfigProvider extends ConfigProvider[OffersConfig] with FailFastConfigProvider[OffersConfig] {
    override protected def load: IO[OffersConfig] = IO.pure(OffersConfig(""))
  }

  val clock = TestClock.apply
  val cu = genCustomerUser
  val cuds = stub[ConsumerDataService]
  (cuds.getByHash _).when(*).returns(IO.pure(cu)).anyNumberOfTimes()

  val mcs = stub[MerchantCacheService]
  val cq = stub[ConsumerQueue]
  val ts = new TrackingService(cuds, mcs, cq)
  val pts = new PartnerTrackingService(cuds)

  override def beforeAll(): Unit = {
    super.beforeAll()
  }

  "TrackingServiceTest" should {
    "create and decode tracking hash" in {
      val offer = randomString

      val before = Instant.now
      Thread.sleep(100)

      val thash = TrackingOps.calculateTrackingHash(cu, offer)
      val tp = pts.decodeTrackingHash(thash).unsafeRunSync()

      Thread.sleep(100)
      val after = Instant.now

      tp.user.userId shouldBe cu.userId
      tp.user.customerName shouldBe cu.customerName
      tp.offer shouldBe Some( offer )
      tp.time.get.isAfter(before) shouldBe true
      tp.time.get.isBefore(after) shouldBe true
    }

    "batch decode tracking hashes" in {

      val offer1 = randomString
      val offer2 = randomString

      val cons1 = genCustomerUser
      val cons2 = genCustomerUser
      val cons3 = genCustomerUser

      val hash1 = TrackingOps.calculateTrackingHash(cons1, offer1)
      val hash2 = TrackingOps.calculateTrackingHash(cons1, offer2)
      val hash3 = TrackingOps.calculateTrackingHash(cons2, offer1)
      val hash4 = TrackingOps.calculateTrackingHash(cons2, offer2)
      val hash5 = TrackingOps.calculateTrackingHash(cons3, offer1)
      val hash6 = TrackingOps.calculateTrackingHash(cons3, offer2)
      val hash7 = "123"
      val hash8 = "321"

      val hashes = (
          hash1
      ::  hash2
      ::  hash3
      ::  hash4
      ::  hash5
      ::  hash6
      ::  hash7
      ::  hash8
      ::  Nil
      )

      (cuds.getByHashBatch _).when(*).returns(IO.pure(cons1 :: cons2 :: Nil))
      val resultMap = pts.decodeHashesBatchAndGetTrackingParams(hashes).unsafeRunSync()

      resultMap(hash1) shouldBe a[Success[_]]
      resultMap(hash2) shouldBe a[Success[_]]
      resultMap(hash3) shouldBe a[Success[_]]
      resultMap(hash4) shouldBe a[Success[_]]
      resultMap(hash5) shouldBe a[Failure[_]]
      resultMap(hash6) shouldBe a[Failure[_]]
      resultMap(hash7) shouldBe a[Failure[_]]
      resultMap(hash8) shouldBe a[Failure[_]]
    }
  }

  def genMerc: MerchantRow = Generator[MerchantRow].apply

  def genCustomerUser: Consumer = Consumer(randomString, randomString)
}
