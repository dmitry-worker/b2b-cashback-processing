package com.noproject.common.domain

import java.io.File

import cats.effect.IO
import com.noproject.common.domain.model.transaction.CashbackTransaction
import org.iq80.leveldb.{DB, Options}
import org.fusesource.leveldbjni.JniDBFactory._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import com.noproject.common.domain.codec.DomainCodecs._

import scala.collection.JavaConverters._

class LevelDBPersistenceTest extends WordSpec with Matchers with BeforeAndAfterAll {

  var levelDB: DB = {
    val options = new Options
    options.createIfMissing(true)
    factory.open(new File("example.db"), options)
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
  }

  override protected def afterAll(): Unit = {
    levelDB.close()
    super.afterAll()
  }

  "LevelDBPersistenceTest" should {

    "basic read/write" in {
      levelDB.put(bytes("key"), bytes("value"))
      val result1 = asString( levelDB.get(bytes("key")) )
      result1 shouldEqual "value"
      levelDB.delete(bytes("key"))
      val result2 = asString( levelDB.get(bytes("key")) )
      result2 shouldEqual null
    }

    "stream read/write" in {
      levelDB.put(bytes("key1"), bytes("value1"))
      levelDB.put(bytes("key2"), bytes("value2"))
      levelDB.put(bytes("key3"), bytes("value3"))
      val iterator = levelDB.iterator()
      iterator.seekToFirst
      val stream = fs2.Stream.fromIterator[IO].apply(iterator.asScala.map(e => asString(e.getKey) -> asString(e.getValue)))
      stream.compile.toList.unsafeRunSync shouldEqual List("key1" -> "value1", "key2" -> "value2", "key3" -> "value3")
      iterator.close()
    }

  }
}
