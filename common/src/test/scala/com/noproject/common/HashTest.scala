package com.noproject.common

import com.noproject.common.data.gen.RandomValueGenerator
import com.noproject.common.security.Hash
import org.scalatest.{Matchers, WordSpec}

class HashTest extends WordSpec with Matchers {

  "HashTest" should {
    "encode and decode base64Url" in {
      val string10 = RandomValueGenerator.randomStringLen(10)
      Hash.base64.decode(Hash.base64.encode(string10)) shouldEqual string10

      val string11 = RandomValueGenerator.randomStringLen(11)
      Hash.base64.decode(Hash.base64.encode(string11)) shouldEqual string11

      val string12 = RandomValueGenerator.randomStringLen(12)
      Hash.base64.decode(Hash.base64.encode(string12)) shouldEqual string12

      val urlunsafe = "%:&?="
      Hash.base64.decode(Hash.base64.encode(urlunsafe)) shouldEqual urlunsafe

    }
  }
}
