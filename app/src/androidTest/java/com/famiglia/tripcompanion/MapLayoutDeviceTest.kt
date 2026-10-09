package com.famiglia.tripcompanion

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.famiglia.tripcompanion.data.Place
import com.famiglia.tripcompanion.maps.*
import com.famiglia.tripcompanion.ui.MapLayout
import com.famiglia.tripcompanion.ui.TripTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Checks real window/layout/input behavior with a controlled renderer, without Google requests. */
@RunWith(AndroidJUnit4::class)
class MapLayoutDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private lateinit var model: MapViewModel
    private val lookup = FakePlaces()
    private val saved = mutableListOf<Pair<String, String>>()
    private var places by mutableStateOf<List<Place>>(emptyList())
    private var fontScale by mutableFloatStateOf(1f)

    @OptIn(ExperimentalMaterial3Api::class)
    @Before fun setUp() {
        device.executeShellCommand("wm density 160")
        device.executeShellCommand("wm size 400x900")
        model = MapViewModel(ApplicationProvider.getApplicationContext(), SavedStateHandle(), lookup)
        compose.setContent {
            TripTheme("Dark") {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    Scaffold(
                        topBar = { TopAppBar(title = { Text("Trip Companion") }) },
                        bottomBar = { NavigationBar { Text("My trips · Saved places · Map") } },
                    ) { padding ->
                        MapLayout(model, places, null, { id, label -> saved += id to label }, Modifier.padding(padding)) { modifier ->
                            Surface(modifier, color = MaterialTheme.colorScheme.secondaryContainer) { Text("Controlled map renderer") }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @After fun tearDown() {
        compose.runOnIdle { model.clearMap() }
        device.executeShellCommand("wm size reset")
        device.executeShellCommand("wm density reset")
    }

    private fun selectPlace() {
        compose.runOnIdle { model.select("luz-id") }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("map-save").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun searchResultsOverlayTheMapAndSelectionKeepsSaveVisibleWithUniformPadding() {
        val originalHeight = compose.onNodeWithTag("map-viewport").fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithTag("map-search").performTextInput("Bari")
        assertTrue(lookup.searches.isEmpty())
        compose.onNodeWithContentDescription("Search places").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("map-results").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(originalHeight, compose.onNodeWithTag("map-viewport").fetchSemanticsNode().boundsInRoot.height, 1f)
        compose.onNodeWithText("Luz restaurant").performClick()
        compose.onNodeWithTag("map-results").assertDoesNotExist()
        compose.onNodeWithTag("map-save").assertIsDisplayed()
        compose.onNodeWithTag("map-search").assertIsNotFocused()
        val root = compose.onNodeWithTag("map-layout").fetchSemanticsNode().boundsInRoot
        val map = compose.onNodeWithTag("map-viewport").fetchSemanticsNode().boundsInRoot
        val surround = compose.onNodeWithTag("map-card-surround").fetchSemanticsNode().boundsInRoot
        val card = compose.onNodeWithTag("map-place-card").fetchSemanticsNode().boundsInRoot
        assertTrue("Map should occupy most of the selected-place screen", map.height > root.height * 0.5f)
        assertEquals(map.bottom, surround.top, 1f)
        assertEquals(card.left - surround.left, card.top - surround.top, 1f)
        assertEquals(card.left - surround.left, surround.bottom - card.bottom, 1f)
        assertTrue(saved.isEmpty())
        compose.onNodeWithTag("map-save").performClick()
        assertEquals(listOf("luz-id" to "Bari"), saved)
        assertEquals(listOf("Bari"), lookup.searches)
    }

    @Test fun expandingTheMapKeepsThePlaceAndSaveAndBackRestoresSearch() {
        selectPlace()
        val initialHeight = compose.onNodeWithTag("map-viewport").fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithContentDescription("Expand map").performClick()
        compose.onNodeWithTag("map-search").assertDoesNotExist()
        compose.onNodeWithTag("map-save").assertIsDisplayed()
        assertTrue(compose.onNodeWithTag("map-viewport").fetchSemanticsNode().boundsInRoot.height > initialHeight)
        device.pressBack()
        compose.onNodeWithTag("map-search").assertIsDisplayed()
        compose.onNodeWithTag("map-save").assertIsDisplayed()
        compose.onNodeWithContentDescription("Map information").performClick()
        compose.onNodeWithText("Google privacy").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Maps terms").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("Expand place details").performClick()
        compose.onNodeWithTag("map-place-details").assertIsDisplayed()
        compose.onNodeWithTag("map-save").assertIsDisplayed()
        compose.onNodeWithContentDescription("Collapse place details").performClick()
        assertTrue(saved.isEmpty())
    }

    @Test fun shortAndWideWindowsKeepSaveUsableAndSavedPlacesDoNotOfferDuplicates() {
        selectPlace()
        device.executeShellCommand("wm size 400x520")
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Map options").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("map-save").assertIsDisplayed()
        assertTrue(compose.onNodeWithTag("map-viewport").fetchSemanticsNode().boundsInRoot.height > 40f)
        compose.onNodeWithContentDescription("Map options").performClick()
        compose.onNodeWithText("Expand map").performClick()
        compose.onNodeWithTag("map-save").assertIsDisplayed()
        compose.onNodeWithContentDescription("Exit expanded map").performClick()
        device.executeShellCommand("wm size 1000x900")
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Map options").fetchSemanticsNodes().isEmpty() }
        compose.runOnIdle { fontScale = 1.5f }
        compose.onNodeWithTag("map-save").assertIsDisplayed()
        compose.onNodeWithTag("map-save").performClick()
        assertEquals(listOf("luz-id" to "Saved place"), saved)
        compose.runOnIdle { places = listOf(Place(name = "My Luz", googlePlaceId = "luz-id")) }
        compose.onNodeWithText("Saved as My Luz").assertIsDisplayed()
        compose.onNodeWithTag("map-save").assertDoesNotExist()
    }

    private class FakePlaces : PlaceLookup {
        val searches = mutableListOf<String>()
        override suspend fun search(query: String): List<PlaceSuggestion> {
            searches += query
            return listOf(PlaceSuggestion("luz-id", "Luz restaurant", "Bari"), PlaceSuggestion("museum-id", "Museum", "Bari"))
        }
        override suspend fun details(id: String, fromSearch: Boolean) = MapLocation(
            id, "Luz", "Via Giuseppe Re David, 32, 70126 Bari BA, Italy", 41.1, 16.9,
        )
    }
}
