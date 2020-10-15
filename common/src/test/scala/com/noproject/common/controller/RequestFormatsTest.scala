package com.noproject.common.controller

import java.time.Instant

import io.circe.parser.decode
import org.scalatest.{Matchers, WordSpec}

class RequestFormatsTest extends WordSpec with Matchers
{

  import com.noproject.common.codec.json.InstantCodecs._

  "RequestsFormats" should {

    "parse zulu formatted Instants" in {
      val want = Instant.parse( "2019-10-12T23:59:59Z" )
      val found = decode[Instant]( "\"2019-10-12T23:59:59Z\"" )
      found.right.get shouldEqual want
    }

    "parse offset formatted Instants" in {
      val want = Instant.parse( "2019-10-12T20:00:00Z")
      val found = decode[Instant]( "\"2019-10-12T23:00:00+03:00\"" )
      found.right.get shouldEqual want
    }

    "parse zulu Instant with milliseconds" in {
      val want = Instant.parse( "2019-10-12T23:00:00Z")
      val found = decode[Instant]( "\"2019-10-12T23:00:00.000Z\"")
      found.right.get shouldEqual want
    }

    "parse offset Instant with milliseconds" in {
      val want = Instant.parse( "2019-10-13T03:00:00Z")
      val found = decode[Instant]( "\"2019-10-12T23:00:00.000-04:00\"")
      found.right.get shouldEqual want
    }
  }
}
