package com.famiglia.tripcompanion

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.famiglia.tripcompanion.data.Place
import com.famiglia.tripcompanion.maps.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MapViewModelTest {
    private val lookup = FakeLookup()
    private lateinit var model: MapViewModel
    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        model = MapViewModel(ApplicationProvider.getApplicationContext(), SavedStateHandle(), lookup)
    }
    @After fun tearDown() { model.clearMap(); Dispatchers.resetMain() }

    @Test fun searchIsExplicitAndEmptyQueriesMakeNoRequests() = runTest {
        model.search(); runCurrent()
        assertTrue(lookup.queries.isEmpty())
        model.changeQuery("  café Bari  "); runCurrent()
        assertTrue(lookup.queries.isEmpty())
        model.search(); runCurrent()
        assertEquals(listOf("café Bari"), lookup.queries)
        assertEquals("café Bari", model.state.value.suggestions.single().title)
        assertFalse(model.state.value.busy)
    }

    @Test fun changingQueryCancelsStaleResults() = runTest {
        val barrier = CompletableDeferred<Unit>()
        lookup.barrier = barrier
        model.changeQuery("Old query"); model.search(); runCurrent()
        assertTrue(model.state.value.busy)
        model.changeQuery("New query")
        lookup.barrier = null
        model.search(); runCurrent()
        barrier.complete(Unit); runCurrent()
        assertEquals("New query", model.state.value.suggestions.single().title)
        assertFalse(model.state.value.busy)
    }

    @Test fun selectingSearchResultTerminatesSessionAndKeepsDetailsOnlyInMemory() = runTest {
        model.changeQuery("Bari"); model.search(); runCurrent()
        model.select("google-id", fromSearch = true); runCurrent()
        assertEquals(listOf("google-id" to true), lookup.detailsCalls)
        assertEquals("google-id", model.state.value.selected?.id)
        assertTrue(model.state.value.suggestions.isEmpty())
        model.clearMap()
        assertNull(model.state.value.selected)
    }

    @Test fun savedPlacesSendOnlyDistinctIdsAndRetainSuccessfulPinsOnFailure() = runTest {
        lookup.failedId = "bad-id"
        model.loadSavedPlaces(listOf(
            Place(name = "Private name", address = "Private address", notes = "Private booking ABC123", googlePlaceId = "google-id"),
            Place(name = "Duplicate", googlePlaceId = "google-id"),
            Place(name = "Manual", notes = "Do not upload"),
            Place(name = "Unavailable", googlePlaceId = "bad-id"),
        ))
        runCurrent()
        assertEquals(listOf("google-id" to false, "bad-id" to false), lookup.detailsCalls)
        assertTrue(lookup.queries.isEmpty())
        assertEquals("google-id", model.state.value.pins.single().id)
        assertNotNull(model.state.value.message)
        assertFalse(model.state.value.busy)
        model.select("google-id"); runCurrent()
        assertEquals(2, lookup.detailsCalls.size)
        assertEquals("google-id", model.state.value.selected?.id)
    }

    @Test fun tappingAnotherMapPlaceDoesNotReusePreviousSearchOrAutocompleteSession() = runTest {
        model.changeQuery("Museum Bari"); model.search(); runCurrent()
        model.select("museum-id", fromSearch = true); runCurrent()
        assertEquals("Museum Bari", model.state.value.saveLabel)

        model.select("cafe-id"); runCurrent()
        assertEquals("cafe-id", model.state.value.selected?.id)
        assertEquals("", model.state.value.saveLabel)
        assertTrue(model.state.value.suggestions.isEmpty())
        assertEquals(listOf("Museum Bari"), lookup.queries)
        assertEquals(listOf("museum-id" to true, "cafe-id" to false), lookup.detailsCalls)
    }

    @Test fun missingKeyAndProviderErrorsAreHandledWithoutLeakingSdkMessages() = runTest {
        val unavailable = MapViewModel(ApplicationProvider.getApplicationContext(), SavedStateHandle(), null)
        assertFalse(unavailable.configured)
        unavailable.changeQuery("Bari"); unavailable.search(); runCurrent()
        assertNotNull(unavailable.state.value.message)
        lookup.failedId = "bad-id"
        model.select("bad-id"); runCurrent()
        assertFalse(model.state.value.busy)
        assertFalse(model.state.value.message.orEmpty().contains("sensitive request details"))
    }

    @Test fun recreatedMapScopeKeepsSelectionAndChangingTripClearsIt() = runTest {
        model.activateScope(1L)
        model.select("google-id"); runCurrent()
        model.activateScope(1L)
        assertEquals("google-id", model.state.value.selected?.id)
        model.activateScope(2L)
        assertNull(model.state.value.selected)
        assertTrue(model.state.value.pins.isEmpty())
    }

    @Test fun richerDetailsAreExplicitAndCachedOnlyForTheCurrentSelection() = runTest {
        model.select("cafe-id"); runCurrent()
        assertTrue(lookup.richCalls.isEmpty())
        model.loadDetails(); runCurrent()
        assertEquals(listOf("cafe-id"), lookup.richCalls)
        assertEquals(4.5, model.state.value.details?.rating)
        model.loadDetails(); runCurrent()
        assertEquals(1, lookup.richCalls.size)
        model.dismissSelection()
        assertNull(model.state.value.selected)
        assertNull(model.state.value.details)
    }

    @Test fun aLateCancelledRichResponseCannotReplaceTheNewSelection() = runTest {
        model.select("old-id"); runCurrent()
        val gate = CompletableDeferred<Unit>()
        lookup.richBarrier = gate
        model.loadDetails(); runCurrent()
        model.select("new-id"); runCurrent()
        lookup.richBarrier = null
        model.loadDetails(); runCurrent()
        gate.complete(Unit); runCurrent()
        assertEquals("new-id", model.state.value.selected?.id)
        assertEquals(listOf("new-id hours"), model.state.value.details?.hours)
        assertFalse(model.state.value.detailsBusy)
    }

    @Test fun richerFailuresKeepBasicDetailsAndAllowAnExplicitRetry() = runTest {
        model.select("cafe-id"); runCurrent()
        lookup.richFailure = true
        model.loadDetails(); runCurrent()
        assertEquals("cafe-id", model.state.value.selected?.id)
        assertFalse(model.state.value.detailsMessage.orEmpty().contains("private request"))
        assertNotNull(model.state.value.detailsMessage)
        lookup.richFailure = false
        model.loadDetails(); runCurrent()
        assertNotNull(model.state.value.details)
        assertNull(model.state.value.detailsMessage)
    }

    @Test fun thumbnailsSendOnlyIdsAndReuseABoundedMemoryCache() = runTest {
        model.thumbnail("first-id"); model.thumbnail("first-id")
        assertEquals(listOf("first-id"), lookup.photoCalls)
        repeat(20) { model.thumbnail("id-$it") }
        model.thumbnail("first-id")
        assertEquals(2, lookup.photoCalls.count { it == "first-id" })
        assertTrue(lookup.queries.isEmpty())
        assertTrue(lookup.detailsCalls.isEmpty())
    }

    @Test fun reviewsAreExplicitCachedAndResetForANewPlace() = runTest {
        model.select("cafe-id"); runCurrent()
        model.loadDetails(); runCurrent()
        assertTrue(lookup.reviewCalls.isEmpty())
        model.loadReviews(); runCurrent()
        assertEquals(listOf("cafe-id"), lookup.reviewCalls)
        assertEquals("cafe-id review", model.state.value.reviews?.reviews?.single()?.text)
        model.loadReviews(); runCurrent()
        assertEquals(1, lookup.reviewCalls.size)
        model.select("museum-id"); runCurrent()
        assertNull(model.state.value.reviews)
        model.loadReviews(); runCurrent()
        assertEquals(listOf("cafe-id", "museum-id"), lookup.reviewCalls)
        model.dismissSelection()
        assertNull(model.state.value.reviews)
    }

    @Test fun emptyReviewsAreCachedAndFailuresAllowExplicitRetryWithoutSdkDetails() = runTest {
        model.select("cafe-id"); runCurrent()
        lookup.reviewFailure = true
        model.loadReviews(); runCurrent()
        assertNotNull(model.state.value.reviewsMessage)
        assertFalse(model.state.value.reviewsMessage.orEmpty().contains("private request"))
        assertFalse(model.state.value.reviewsBusy)
        lookup.reviewFailure = false
        lookup.emptyReviews = true
        model.loadReviews(); runCurrent()
        assertTrue(model.state.value.reviews!!.reviews.isEmpty())
        assertNull(model.state.value.reviewsMessage)
        model.loadReviews(); runCurrent()
        assertEquals(2, lookup.reviewCalls.size)
        model.loadReviews(force = true); runCurrent()
        assertEquals(3, lookup.reviewCalls.size)
    }

    @Test fun lateCancelledReviewsCannotReplaceANewPlaceOrItsLoadingState() = runTest {
        model.select("old-id"); runCurrent()
        val gate = CompletableDeferred<Unit>()
        lookup.reviewBarrier = gate
        model.loadReviews(); runCurrent()
        model.select("new-id"); runCurrent()
        lookup.reviewBarrier = null
        model.loadReviews(); runCurrent()
        gate.complete(Unit); runCurrent()
        assertEquals("new-id review", model.state.value.reviews!!.reviews.single().text)
        assertFalse(model.state.value.reviewsBusy)
    }

    @Test fun closingReviewsCancelsInflightResponseAndReopeningCanRetry() = runTest {
        model.select("cafe-id"); runCurrent()
        val gate = CompletableDeferred<Unit>()
        lookup.reviewBarrier = gate
        model.loadReviews(); runCurrent()
        model.loadReviews(); runCurrent()
        assertEquals(1, lookup.reviewCalls.size)
        model.cancelReviewsLoad()
        assertFalse(model.state.value.reviewsBusy)
        gate.complete(Unit); runCurrent()
        assertNull(model.state.value.reviews)
        lookup.reviewBarrier = null
        model.loadReviews(); runCurrent()
        assertEquals("cafe-id review", model.state.value.reviews!!.reviews.single().text)
    }

    @Test fun closingAPlaceDoesNotRefitSavedPinsOrRequestAnotherCameraMove() = runTest {
        model.loadSavedPlaces(listOf(Place(name = "First", googlePlaceId = "first"), Place(name = "Far away", googlePlaceId = "second")))
        runCurrent()
        val fit = model.state.value.cameraRequest!!
        assertEquals(listOf("first", "second"), fit.locations.map { it.id })
        model.cameraHandled(fit.sequence)
        model.select("first"); runCurrent()
        val focus = model.state.value.cameraRequest!!
        assertEquals(listOf("first"), focus.locations.map { it.id })
        model.cameraHandled(focus.sequence)
        model.updateViewport(MapArea(41.12, 16.92, 1000.0))
        model.dismissSelection(); runCurrent()
        assertNull(model.state.value.cameraRequest)
        assertEquals(2, model.state.value.pins.size)
        assertEquals(MapArea(41.12, 16.92, 1000.0), model.state.value.viewport)
        model.select("second"); runCurrent()
        assertTrue(model.state.value.cameraRequest!!.sequence > focus.sequence)
        model.cameraHandled(focus.sequence) // A cancelled older animation cannot consume the new command.
        assertNotNull(model.state.value.cameraRequest)
        model.dismissSelection()
        assertNull(model.state.value.cameraRequest)
    }

    @Test fun discoveryIsExplicitBoundedAndUsesTheLatestAreaWithoutMovingTheCamera() = runTest {
        val first = MapArea(41.1, 16.9, 1200.0)
        val second = MapArea(41.2, 16.9, 2000.0)
        model.updateViewport(first); runCurrent()
        assertTrue(lookup.nearbyCalls.isEmpty())
        model.discover(NearbyCategory.Cafes); runCurrent()
        assertEquals(listOf(NearbyCategory.Cafes to first), lookup.nearbyCalls)
        assertEquals(20, model.state.value.nearbyPins.size)
        assertFalse(model.state.value.areaChanged)
        assertNull(model.state.value.cameraRequest)
        model.updateViewport(second); runCurrent()
        assertEquals(1, lookup.nearbyCalls.size)
        assertTrue(model.state.value.areaChanged)
        model.searchThisArea(); runCurrent()
        assertEquals(NearbyCategory.Cafes to second, lookup.nearbyCalls.last())
        assertFalse(model.state.value.areaChanged)
        model.select("nearby-0"); runCurrent()
        assertTrue(lookup.detailsCalls.isEmpty()) // Basic details already arrived with the nearby search.
        assertEquals("nearby-0", model.state.value.selected?.id)
        model.dismissSelection()
        assertEquals(20, model.state.value.nearbyPins.size)
        assertNull(model.state.value.cameraRequest)
    }

    @Test fun oversizedAreasAndUnavailableMapsDoNotMakeNearbyRequests() = runTest {
        model.discover(NearbyCategory.Restaurants); runCurrent()
        assertNotNull(model.state.value.nearbyMessage)
        model.updateViewport(MapArea(0.0, 0.0, 50_001.0))
        model.discover(NearbyCategory.Restaurants); runCurrent()
        assertTrue(model.state.value.nearbyMessage!!.contains("Zoom in"))
        model.updateViewport(MapArea(Double.NaN, 0.0, 500.0))
        model.discover(NearbyCategory.Restaurants); runCurrent()
        assertTrue(lookup.nearbyCalls.isEmpty())
    }

    @Test fun clearingDiscoveryPreservesSavedPinsAndCancelsLateResults() = runTest {
        model.loadSavedPlaces(listOf(Place(name = "Private", notes = "Booking secret", googlePlaceId = "saved-id"))); runCurrent()
        val gate = CompletableDeferred<Unit>()
        lookup.nearbyBarrier = gate
        model.updateViewport(MapArea(41.1, 16.9, 1500.0))
        model.discover(NearbyCategory.Parks); runCurrent()
        model.discover(NearbyCategory.Parks); runCurrent()
        assertEquals(1, lookup.nearbyCalls.size)
        model.clearNearby()
        gate.complete(Unit); runCurrent()
        assertNull(model.state.value.nearbyCategory)
        assertTrue(model.state.value.nearbyPins.isEmpty())
        assertEquals("saved-id", model.state.value.pins.single().id)
        assertFalse(model.state.value.busy)
        assertTrue(lookup.queries.isEmpty())
    }

    @Test fun nearbyFailuresAndEmptyResultsCanBeRetriedWithoutLeakingRequests() = runTest {
        model.updateViewport(MapArea(41.1, 16.9, 1000.0))
        lookup.nearbyFailure = true
        model.discover(NearbyCategory.Attractions); runCurrent()
        assertNotNull(model.state.value.message)
        assertFalse(model.state.value.message!!.contains("private request"))
        assertFalse(model.state.value.busy)
        lookup.nearbyFailure = false
        lookup.nearbyEmpty = true
        model.discover(NearbyCategory.Attractions); runCurrent()
        assertNull(model.state.value.message)
        assertTrue(model.state.value.nearbyMessage!!.contains("No attractions"))
        model.changeQuery("Bari")
        assertNull(model.state.value.nearbyCategory)
        assertTrue(model.state.value.nearbyPins.isEmpty())
        assertTrue(lookup.queries.isEmpty())
    }

    @Test fun movingTheAreaWhileSearchingOffersAnExplicitRefreshForThatNewArea() = runTest {
        val gate = CompletableDeferred<Unit>()
        lookup.nearbyBarrier = gate
        val first = MapArea(41.1, 16.9, 1000.0)
        model.updateViewport(first)
        model.discover(NearbyCategory.Shopping); runCurrent()
        model.updateViewport(MapArea(42.0, 16.9, 1000.0))
        gate.complete(Unit); runCurrent()
        assertEquals(first, model.state.value.searchedArea)
        assertTrue(model.state.value.areaChanged)
        assertEquals(1, lookup.nearbyCalls.size)
    }

    @Test fun layoutAndAutomaticFocusChangesDoNotOfferAnUnrequestedAreaRefresh() = runTest {
        model.updateViewport(MapArea(41.1, 16.9, 1000.0))
        model.discover(NearbyCategory.Cafes); runCurrent()
        val changed = MapArea(41.2, 16.9, 1500.0)
        model.updateViewport(changed, userMoved = false)
        assertEquals(changed, model.state.value.viewport)
        assertFalse(model.state.value.areaChanged)
        assertEquals(1, lookup.nearbyCalls.size)
        model.updateViewport(changed, userMoved = true)
        assertTrue(model.state.value.areaChanged)
        model.updateViewport(changed.copy(radiusMeters = 2000.0), userMoved = false)
        assertTrue(model.state.value.areaChanged) // Padding changes must not erase an already pending refresh.
        val gate = CompletableDeferred<Unit>()
        lookup.nearbyBarrier = gate
        model.searchThisArea(); runCurrent()
        model.updateViewport(changed.copy(radiusMeters = 3000.0), userMoved = false)
        gate.complete(Unit); runCurrent()
        assertFalse(model.state.value.areaChanged) // Loading/result overlays do not count as a pan during the request.
    }

    private class FakeLookup : PlaceLookup {
        val nearbyCalls = mutableListOf<Pair<NearbyCategory, MapArea>>()
        var nearbyBarrier: CompletableDeferred<Unit>? = null
        var nearbyFailure = false
        var nearbyEmpty = false
        override suspend fun nearby(category: NearbyCategory, area: MapArea): List<MapLocation> {
            nearbyCalls += category to area
            val gate = nearbyBarrier
            if (gate != null) withContext(NonCancellable) { gate.await() }
            if (nearbyFailure) error("private request")
            return if (nearbyEmpty) emptyList() else (0..25).map { MapLocation("nearby-$it", "Place $it", "Public address", area.latitude, area.longitude) }
        }
        val queries = mutableListOf<String>()
        val detailsCalls = mutableListOf<Pair<String, Boolean>>()
        var barrier: CompletableDeferred<Unit>? = null
        var failedId: String? = null
        val richCalls = mutableListOf<String>()
        val photoCalls = mutableListOf<String>()
        val reviewCalls = mutableListOf<String>()
        var reviewBarrier: CompletableDeferred<Unit>? = null
        var reviewFailure = false
        var emptyReviews = false
        override suspend fun reviews(id: String): PlaceReviews {
            reviewCalls += id
            val gate = reviewBarrier
            if (gate != null) withContext(NonCancellable) { gate.await() }
            if (reviewFailure) error("private request")
            return PlaceReviews(if (emptyReviews) emptyList() else listOf(PlaceReview("Author", text = "$id review")))
        }
        var richBarrier: CompletableDeferred<Unit>? = null
        var richFailure = false
        override suspend fun thumbnail(id: String): PlacePhoto? { photoCalls += id; return null }
        override suspend fun moreDetails(id: String): PlaceDetails {
            richCalls += id
            val gate = richBarrier
            if (gate != null) withContext(NonCancellable) { gate.await() }
            if (richFailure) error("private request")
            return PlaceDetails(rating = 4.5, hours = listOf("$id hours"))
        }
        override suspend fun search(query: String): List<PlaceSuggestion> {
            queries += query
            barrier?.await()
            return listOf(PlaceSuggestion("google-id", query, "Bari"))
        }
        override suspend fun details(id: String, fromSearch: Boolean): MapLocation {
            detailsCalls += id to fromSearch
            if (id == failedId) error("sensitive request details")
            return MapLocation(id, "Google name", "Google address", 41.1, 16.9)
        }
    }
}
