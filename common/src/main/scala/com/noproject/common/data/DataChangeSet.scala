package com.noproject.common.data

import cats.Eq

case class DataChangeSet[K, V: Eq](
  create: DataChangeSetContents[K, V]
, update: DataChangeSetContents[K, V]
, delete: DataChangeSetContents[K, V]
)
