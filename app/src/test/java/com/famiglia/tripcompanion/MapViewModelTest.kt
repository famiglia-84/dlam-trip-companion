package com.famiglia.tripcompanion

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.famiglia.tripcompanion.data.Place
import com.famiglia.tripcompanion.maps.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    private class FakeLookup : PlaceLookup {
        val queries = mutableListOf<String>()
        val detailsCalls = mutableListOf<Pair<String, Boolean>>()
        var barrier: CompletableDeferred<Unit>? = null
        var failedId: String? = null
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
