package com.famiglia.tripcompanion.maps

import android.net.Uri

object PlaceLinks {
    fun view(place: MapLocation): Uri = Uri.parse("https://www.google.com/maps/search/").buildUpon()
        .appendQueryParameter("api", "1").appendQueryParameter("query", "${place.latitude},${place.longitude}")
        .appendQueryParameter("query_place_id", place.id).build()
    fun directions(place: MapLocation): Uri = Uri.parse("https://www.google.com/maps/dir/").buildUpon()
        .appendQueryParameter("api", "1").appendQueryParameter("destination", "${place.latitude},${place.longitude}")
        .appendQueryParameter("destination_place_id", place.id).build()
    fun website(value: String?): Uri? = value?.let { Uri.parse(it) }
        ?.takeIf { it.scheme in listOf("http", "https") && !it.host.isNullOrBlank() }
}
