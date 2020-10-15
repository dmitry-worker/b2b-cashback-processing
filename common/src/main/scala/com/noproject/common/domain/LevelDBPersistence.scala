package com.noproject.common.domain

import org.iq80.leveldb._
import org.fusesource.leveldbjni.JniDBFactory._
import java.io._

import cats.effect.{IO, Resource}
import com.noproject.common.codec.json.JsonEncoded
import com.noproject.common.domain.model.transaction.CashbackTransaction

import scala.collection.JavaConverters._
import io.circe.syntax._
import io.circe.parser._
import com.noproject.common.domain.codec.DomainCodecs._
import io.circe.{Decoder, Encoder, Json}

object LevelDBPersistence {

  def getInstance[A](fileName: String)(implicit enc: Encoder[A], dec: Decoder[A]): Resource[IO, LevelDBPersistence[A]] = {
    val acquire: IO[LevelDBPersistence[A]] = IO.delay {
      val options = new Options
      options.createIfMissing(true)
      val db = factory.open(new File(fileName), options)
      LevelDBPersistence(db)
    }

    Resource.make[IO, LevelDBPersistence[A]](acquire)(_.close)
  }

}


case class LevelDBPersistence[A](private val db: DB)(implicit enc: Encoder[A], dec: Decoder[A]) {


  def close: IO[Unit] = IO.delay { db.close() }


  def insertRecord(id: String, txn: A): IO[Unit] = IO.delay {
    db.put(bytes(id), bytes(txn.asJson.noSpaces))
  }


  def insertRecords(txns: List[(String, A)]): IO[Unit] = IO.delay {
    val batch = db.createWriteBatch()
    txns.foreach { case (id, txn) => batch.put( bytes(id), bytes(txn.asJson.noSpaces) ) }
    db.write(batch)
  }


  def getTxnStream: fs2.Stream[IO, A] = {
    val iterator = db.iterator()
    iterator.seekToFirst()
    val scalaIter = iterator.asScala.map(e => asString(e.getValue))
    fs2.Stream.fromIterator[IO].apply(scalaIter).flatMap { s =>
      parse(s).flatMap(dec.decodeJson) match {
        case Left(e)  => fs2.Stream.empty
        case Right(a) => fs2.Stream.emit(a)
      }
    }
  }


  def removeTxnsByRef(refs: List[String]): IO[Unit] = IO.delay {
    val batch = db.createWriteBatch()
    refs.foreach { ref => batch.delete( bytes(ref) ) }
    db.write(batch)
  }


}