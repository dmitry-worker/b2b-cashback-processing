package com.noproject.common.domain.model

import org.postgis.Point

object Location {

  def from(point: Point): Location = Location(point.x, point.y)


  def apply(point: Point): Location = new Location(point.getX, point.getY)

  def distance( l1: Location, l2: Location) : Int = {
    val R = 6371000; // metres
    val e1 = l1.lat.toRadians
    val e2 = l2.lat.toRadians
    val de = (l2.lat-l1.lat).toRadians
    val da = (l2.lon-l1.lon).toRadians

    val a = Math.sin(de/2) * Math.sin(de/2) +
      Math.cos(e1) * Math.cos(e2) *
        Math.sin(da/2) * Math.sin(da/2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a))

    (R * c).toInt
  }
}

case class Location(lon: Double, lat: Double) {
  def toPoint = new Point(lon, lat)
}