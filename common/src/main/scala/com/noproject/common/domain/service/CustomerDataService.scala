package com.noproject.common.domain.service

import java.time.Clock

import cats.effect.IO
import com.noproject.common.Exceptions.{CustomerNotFoundException, ProgrammerError}
import com.noproject.common.domain.DefaultPersistence
import com.noproject.common.domain.dao.customer.CustomerDAO
import com.noproject.common.domain.model.customer.{AccessRole, Customer, CustomerUtil}
import javax.inject.{Inject, Singleton}

// Should be encapsulated into a parent class for all services
import doobie._
import doobie.implicits._

import scala.language.implicitConversions


/*
 working with CustomerDao
 */
@Singleton
class CustomerDataService @Inject()( customerDAO: CustomerDAO
                                   , sp : DefaultPersistence
                                   , clock : Clock ) {

  implicit def transaction[T]( cio : ConnectionIO[T] ) : IO[T] = {
    cio.transact( sp.xar )
  }

  def getByKey(key: String): IO[Customer] = {
    customerDAO.getByKey(key).map {
      case None       => throw CustomerNotFoundException(key)
      case Some(cust) => cust
    }
  }

  def getByName(name: String): IO[Customer] = {
    customerDAO.getByName(name).map {
      case None       => throw CustomerNotFoundException(name)
      case Some(cust) => cust
    }
  }

  def create(name: String, key: String, secret: String, role: Set[AccessRole]): IO[Int] = {
    create0(name, key, secret, role).flatMap( c =>  customerDAO.insert(c :: Nil) )
  }

  def createIfNotExists(name: String, key: String, secret: String, role: Set[AccessRole]): IO[Int] = {
    create0(name, key, secret, role).flatMap( c =>  customerDAO.insertIfNotExists(c :: Nil) )
  }

  private def create0(name: String, key: String, secret: String, role: Set[AccessRole]): IO[Customer] = {
    for {
      hash <- CustomerUtil.calculateHash(name, key, secret)
      customer = Customer(name, key, hash, role, None, None, webhookActive = false)
    } yield customer
  }

  def findAll: IO[List[Customer]] = customerDAO.findAll

  def delete(key: String): IO[Int] = customerDAO.delete(key).map {
    case 0 => throw CustomerNotFoundException( key )
    case 1 => 1
    case _ => throw ProgrammerError( "Deleted more than one customer by api key, database is broken.")
  }

  def updateRoles( key: String, roles: Set[AccessRole] ): IO[ Customer ] = {
    // Combine ConnectionIO actions in order to ensure transactional integrity
    // and maintain DAO encapsulation.
    for {
      // Update the roles
      _ <- customerDAO.updateRoles( key, roles ).map {
        case 0 => throw CustomerNotFoundException( key )
        case 1 => 1
        case _ => throw ProgrammerError( "Multiple rows modified in update on unique key, database is broken" )
      }

      // Unfortunately, we require this second round trip, since the session table
      // is indexed by name, but the change request was for API key.
      customer <- customerDAO
        .getByKeyTxn( key )
        .map( _.getOrElse( throw CustomerNotFoundException( key ) ) )
    } yield customer
  }
}
