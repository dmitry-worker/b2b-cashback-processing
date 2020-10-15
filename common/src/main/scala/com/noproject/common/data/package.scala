package com.noproject.common

import cats.Eq

package object data {

  type DataChangeSetContents[K, V] = DataUnordered[K, ElementChange[V]]

}
