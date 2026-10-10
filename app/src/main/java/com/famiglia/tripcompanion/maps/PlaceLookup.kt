package com.famiglia.tripcompanion.maps

import android.content.Context
import com.famiglia.tripcompanion.R
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.FetchResolvedPhotoUriRequest
import com.google.android.libraries.places.api.net.SearchNearbyRequest
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.ExperimentalCoroutinesApi

data class PlaceSuggestion(val id: String, val title: String, val subtitle: String)
data class MapLocation(
    val id: String, val name: String, val address: String,
    val latitude: Double, val longitude: Double, val attributions: List<String> = emptyList(),
    val category: String = "",
)

data class PhotoCredit(val name: String, val uri: String?)
data class PlacePhoto(val uri: String, val credits: List<PhotoCredit> = emptyList(), val attributionHtml: String = "")
data class PlaceReview(
    val author: String, val authorUri: String? = null, val authorPhotoUri: String? = null,
    val rating: Double? = null, val text: String = "", val relativeTime: String = "", val publishTime: String? = null,
    val translated: Boolean = false, val attributionHtml: String = "", val flagUri: String? = null,
)
data class PlaceReviews(val reviews: List<PlaceReview> = emptyList(), val attributions: List<String> = emptyList())

data class PlaceDetails(
    val photos: List<PlacePhoto> = emptyList(), val rating: Double? = null, val ratingCount: Int? = null,
    val hours: List<String> = emptyList(), val openNow: Boolean? = null,
    val phone: String? = null, val website: String? = null, val attributions: List<String> = emptyList(),
    val photosUnavailable: Boolean = false,
)

/** Only explicit search text, place IDs and map areas cross this boundary, never travel records. */
interface PlaceLookup {
    suspend fun search(query: String): List<PlaceSuggestion>
    suspend fun details(id: String, fromSearch: Boolean = false): MapLocation
    suspend fun thumbnail(id: String): PlacePhoto? = null
    suspend fun moreDetails(id: String): PlaceDetails = PlaceDetails()
    suspend fun reviews(id: String): PlaceReviews = PlaceReviews()
    suspend fun nearby(category: NearbyCategory, area: MapArea): List<MapLocation> = emptyList()
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
            Place.Field.ID, Place.Field.DISPLAY_NAME, Place.Field.FORMATTED_ADDRESS, Place.Field.LOCATION, Place.Field.PRIMARY_TYPE_DISPLAY_NAME,
        )).setCancellationToken(cancellation.token)
        if (fromSearch) {
            session?.let { request.setSessionToken(it) }
            session = null
        }
        val place = client.fetchPlace(request.build()).await(cancellation).place
        val location = requireNotNull(place.location) { "This place has no map location." }
        return MapLocation(id, place.displayName.orEmpty(), place.formattedAddress.orEmpty(),
            location.latitude, location.longitude, place.attributions.orEmpty(), place.primaryTypeDisplayName.orEmpty())
    }

    override suspend fun nearby(category: NearbyCategory, area: MapArea): List<MapLocation> {
        require(area.searchable)
        val cancellation = CancellationTokenSource()
        val request = SearchNearbyRequest.builder(
            CircularBounds.newInstance(LatLng(area.latitude, area.longitude), area.radiusMeters),
            listOf(Place.Field.ID, Place.Field.DISPLAY_NAME, Place.Field.FORMATTED_ADDRESS,
                Place.Field.LOCATION, Place.Field.PRIMARY_TYPE_DISPLAY_NAME),
        ).setIncludedTypes(category.placeTypes).setMaxResultCount(20)
            .setRankPreference(SearchNearbyRequest.RankPreference.DISTANCE)
            .setCancellationToken(cancellation.token).build()
        return client.searchNearby(request).await(cancellation).places.mapNotNull { place ->
            val id = place.id ?: return@mapNotNull null
            val location = place.location ?: return@mapNotNull null
            MapLocation(id, place.displayName.orEmpty(), place.formattedAddress.orEmpty(), location.latitude,
                location.longitude, place.attributions.orEmpty(), place.primaryTypeDisplayName.orEmpty())
        }
    }

    override suspend fun thumbnail(id: String): PlacePhoto? {
        val cancellation = CancellationTokenSource()
        val place = client.fetchPlace(FetchPlaceRequest.builder(id, listOf(Place.Field.PHOTO_METADATAS))
            .setCancellationToken(cancellation.token).build()).await(cancellation).place
        return place.photoMetadatas?.firstOrNull()?.let { photo(it, 288) }
    }

    override suspend fun moreDetails(id: String): PlaceDetails {
        val cancellation = CancellationTokenSource()
        val place = client.fetchPlace(FetchPlaceRequest.builder(id, listOf(
            Place.Field.PHOTO_METADATAS, Place.Field.RATING, Place.Field.USER_RATING_COUNT,
            Place.Field.CURRENT_OPENING_HOURS, Place.Field.OPENING_HOURS, Place.Field.UTC_OFFSET,
            Place.Field.INTERNATIONAL_PHONE_NUMBER, Place.Field.WEBSITE_URI,
            Place.Field.BUSINESS_STATUS,
        )).setCancellationToken(cancellation.token).build()).await(cancellation).place
        var photosUnavailable = false
        val photos = place.photoMetadatas.orEmpty().take(2).mapNotNull {
            try { photo(it, 720) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { photosUnavailable = true; null } // Keep ratings/hours usable when a photo is unavailable.
        }
        return PlaceDetails(photos, place.rating, place.userRatingCount,
            (place.currentOpeningHours ?: place.openingHours)?.weekdayText.orEmpty(),
            runCatching { place.isOpen }.getOrNull(), place.internationalPhoneNumber, place.websiteUri?.toString(), place.attributions.orEmpty(), photosUnavailable)
    }

    // Reviews belong to a higher billing tier: never include them in ordinary detail/photo requests.
    override suspend fun reviews(id: String): PlaceReviews {
        val cancellation = CancellationTokenSource()
        val place = client.fetchPlace(FetchPlaceRequest.builder(id, listOf(Place.Field.REVIEWS))
            .setCancellationToken(cancellation.token).build()).await(cancellation).place
        return PlaceReviews(place.reviews.orEmpty().take(5).map { review ->
            val author = review.authorAttribution
            PlaceReview(author.name, author.uri, author.photoUri, review.rating,
                review.text ?: review.originalText.orEmpty(), review.relativePublishTimeDescription.orEmpty(), review.publishTime,
                translated = !review.textLanguageCode.isNullOrBlank() && !review.originalTextLanguageCode.isNullOrBlank() &&
                    review.textLanguageCode != review.originalTextLanguageCode,
                attributionHtml = review.attribution.orEmpty(), flagUri = review.flagContentUri?.toString())
        }, place.attributions.orEmpty())
    }

    private suspend fun photo(metadata: com.google.android.libraries.places.api.model.PhotoMetadata, size: Int): PlacePhoto {
        val cancellation = CancellationTokenSource()
        val uri = requireNotNull(client.fetchResolvedPhotoUri(FetchResolvedPhotoUriRequest.builder(metadata)
            .setMaxWidth(size).setMaxHeight(size).setCancellationToken(cancellation.token).build()).await(cancellation).uri)
        return PlacePhoto(uri.toString(), metadata.authorAttributions?.asList().orEmpty().map { PhotoCredit(it.name, it.uri) }, metadata.attributions.orEmpty())
    }

    companion object {
        fun create(context: Context): PlaceLookup? {
            val key = context.getString(R.string.google_maps_key).trim()
            return if (key.isEmpty()) null else GooglePlaceLookup(context.applicationContext, key)
        }
    }
}
