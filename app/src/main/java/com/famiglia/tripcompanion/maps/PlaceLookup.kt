package com.famiglia.tripcompanion.maps

import android.content.Context
import com.famiglia.tripcompanion.R
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.ExperimentalCoroutinesApi

data class PlaceSuggestion(val id: String, val title: String, val subtitle: String)
data class MapLocation(
    val id: String, val name: String, val address: String,
    val latitude: Double, val longitude: Double, val attributions: List<String> = emptyList(),
)

/** This boundary accepts only explicit search text or place IDs, never travel records. */
interface PlaceLookup {
    suspend fun search(query: String): List<PlaceSuggestion>
    suspend fun details(id: String, fromSearch: Boolean = false): MapLocation
}

@OptIn(ExperimentalCoroutinesApi::class)
class GooglePlaceLookup private constructor(private val context: Context, private val key: String) : PlaceLookup {
    private val client by lazy {
        if (!Places.isInitialized()) Places.initializeWithNewPlacesApiEnabled(context, key)
        Places.createClient(context)
    }
    private var session: AutocompleteSessionToken? = null

    override suspend fun search(query: String): List<PlaceSuggestion> {
        val token = session ?: AutocompleteSessionToken.newInstance().also { session = it }
        val cancellation = CancellationTokenSource()
        val request = FindAutocompletePredictionsRequest.builder().setQuery(query).setSessionToken(token)
            .setCancellationToken(cancellation.token).build()
        return client.findAutocompletePredictions(request).await(cancellation).autocompletePredictions.map {
            PlaceSuggestion(it.placeId, it.getPrimaryText(null).toString(), it.getSecondaryText(null).toString())
        }
    }

    override suspend fun details(id: String, fromSearch: Boolean): MapLocation {
        val cancellation = CancellationTokenSource()
        val request = FetchPlaceRequest.builder(id, listOf(
            Place.Field.ID, Place.Field.DISPLAY_NAME, Place.Field.FORMATTED_ADDRESS, Place.Field.LOCATION,
        )).setCancellationToken(cancellation.token)
        if (fromSearch) {
            session?.let { request.setSessionToken(it) }
            session = null
        }
        val place = client.fetchPlace(request.build()).await(cancellation).place
        val location = requireNotNull(place.location) { "This place has no map location." }
        return MapLocation(id, place.displayName.orEmpty(), place.formattedAddress.orEmpty(),
            location.latitude, location.longitude, place.attributions.orEmpty())
    }

    companion object {
        fun create(context: Context): PlaceLookup? {
            val key = context.getString(R.string.google_maps_key).trim()
            return if (key.isEmpty()) null else GooglePlaceLookup(context.applicationContext, key)
        }
    }
}
