package com.noproject.common.domain.service

import cats.data.NonEmptyList
import cats.effect.{Concurrent, IO, Timer}
import cats.implicits.catsSyntaxApplicativeId
import com.noproject.common.domain.DefaultPersistence
import doobie.implicits._
import com.noproject.common.domain.dao.customer.{ConsumerDAO, ConsumerProfileDAO}
import com.noproject.common.domain.model.customer.{Consumer, ConsumerProfile}
import doobie.{ConnectionIO, Fragment}
import javax.inject.{Inject, Singleton}

import scala.concurrent.duration._

@Singleton
class ConsumerDataService @Inject()(
  dao:  ConsumerDAO
, pdao: ConsumerProfileDAO
, sp: DefaultPersistence
) {

  def getByHash(hash: String): IO[Consumer] = {
    dao.findByHash(hash).map {
      case None       => Consumer.empty
      case Some(user) => user
    }
  }

  def getByHashBatch(hashes: NonEmptyList[String]): IO[List[Consumer]] = {
    dao.findBatchByHashes(hashes)
  }

  def insertConsumer(customerName: String, id: String, profile: Option[ConsumerProfile]):IO[Unit] = {
    val cons = Consumer(customerName, id)
    val prof = profile.getOrElse(ConsumerProfile.instance(cons.hash))
    val cio = for {
      _ <- dao.insertTxn(cons)
      _ <- pdao.insertTxn(prof)
    } yield ()
    cio.transact(sp.xar)
  }

  def getConsumersByCustomer(customerName: String): IO[List[ConsumerProfile]] = {
    pdao.findAllProfilesByCustomer(customerName)
  }

}
