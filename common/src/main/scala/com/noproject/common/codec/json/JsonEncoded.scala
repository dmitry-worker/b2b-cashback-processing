package com.noproject.common.codec.json

import io.circe.Json



trait JsonEncoded {

  def jsonEncoded: Json

}
