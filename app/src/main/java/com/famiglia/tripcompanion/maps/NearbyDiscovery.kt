package com.famiglia.tripcompanion.maps

import kotlin.math.*

enum class NearbyCategory(val label: String, val placeTypes: List<String>) {
    Restaurants("Restaurants", listOf("restaurant")),
    Cafes("Cafés", listOf("cafe")),
    Attractions("Attractions", listOf("tourist_attraction")),
    Shopping("Shopping", listOf("shopping_mall")),
    Parks("Parks", listOf("park")),
}

/** A circle covering the visible map. No GPS or private travel records are involved. */
data class MapArea(val latitude: Double, val longitude: Double, val radiusMeters: Double) {
    val searchable: Boolean get() = latitude.isFinite() && latitude in -90.0..90.0 &&
        longitude.isFinite() && longitude in -180.0..180.0 && radiusMeters.isFinite() && radiusMeters in 1.0..50_000.0

    fun differsFrom(other: MapArea): Boolean = distanceMeters(latitude, longitude, other.latitude, other.longitude) >
        maxOf(50.0, other.radiusMeters * 0.15) || abs(radiusMeters - other.radiusMeters) > maxOf(50.0, other.radiusMeters * 0.2)

    companion object {
        fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val lat = Math.toRadians(lat2 - lat1)
            val lon = Math.toRadians(lon2 - lon1)
            val a = sin(lat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(lon / 2).pow(2)
            return 6_371_000 * 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
        }
    }
}

/** Only explicit focus/fit actions produce a command; dismissing a card never does. */
data class MapCameraRequest(val sequence: Long, val locations: List<MapLocation>)
