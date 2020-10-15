package com.noproject.domain.service.statistics


// integration service params
trait IntegrationServiceMetrics[F[_]] {

  def createdMerchants: F[Unit]
  def updatedMerchants: F[Unit]
  def deletedMerchants: F[Unit]
  def recordMerchantSyncSuccess: F[Unit]
  def recordMerchantSyncFailure: F[Unit]

}
