package com.noproject.common.domain.service

import cats.data.NonEmptyList
import cats.effect.IO
import com.noproject.common.domain.dao.partner.{CustomerNetworksDAO, NetworkDAO}
import com.noproject.common.domain.model.partner.{CustomerNetwork, Network}
import javax.inject.{Inject, Singleton}

@Singleton
class NetworkDataService @Inject()(networkDAO: NetworkDAO, customerNetworksDAO: CustomerNetworksDAO) {

  def getAllNetworks: IO[List[Network]] = networkDAO.findAll

  def getNetworkNamesForCustomer(name: String): IO[List[String]] = customerNetworksDAO.getNetworkNamesByCustomerName(name)

  def getNetworksForCustomer(id: String): IO[List[CustomerNetwork]] = {
    customerNetworksDAO.getByCustomerName(id)
  }

  def updateCustomerNetworks(customerName: String, networkNames: List[String]): IO[Int] = {
    def difference(exists: List[String]): (List[String], List[String]) = {
      val exSet = exists.toSet
      val nSet = networkNames.toSet
      val namesToInsert = (nSet -- exSet).toList
      val namesToDelete = (exSet -- nSet).toList
      (namesToInsert, namesToDelete)
    }

    for {
      exists <- getNetworkNamesForCustomer(customerName)
      (ni, nd) = difference(exists)
      i <- if (ni.nonEmpty) customerNetworksDAO.insert(NonEmptyList.fromListUnsafe(ni.map { el => customerName -> el  })) else IO(0)
      d <- if (nd.nonEmpty) customerNetworksDAO.delete(customerName, NonEmptyList.fromList(nd).get) else IO(0)
    } yield i + d
  }

  def enableNetwork(id: String, nw: String): IO[Int] = {
    customerNetworksDAO.enableNetwork(id, nw)
  }

  def disableNetwork(id: String, nw: String): IO[Int] = {
    customerNetworksDAO.disableNetwork(id, nw)
  }

}
