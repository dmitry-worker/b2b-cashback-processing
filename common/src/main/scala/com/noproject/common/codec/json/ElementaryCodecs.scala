package com.noproject.common.codec.json

trait ElementaryCodecs
  extends EnumerationCodecs
    with InstantCodecs
    with FuuidCodecs
    with ValueClassCodecs

object ElementaryCodecs extends ElementaryCodecs

