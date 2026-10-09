package com.famiglia.tripcompanion

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private lateinit var model: MapViewModel
    private val lookup = FakePlaces()
    private val saved = mutableListOf<Triple<String, String, String>>()
    private var places by mutableStateOf<List<Place>>(emptyList())
    private var fontScale by mutableFloatStateOf(1f)
    @Volatile private var keyboardVisible = false
    private var previousKeyboardSetting = ""

    @OptIn(ExperimentalMaterial3Api::class)
    @Before fun setUp() {
        device.executeShellCommand("wm density 160")
        device.executeShellCommand("wm size 400x900")
        previousKeyboardSetting = device.executeShellCommand("settings get secure show_ime_with_hard_keyboard").trim()
        device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        model = MapViewModel(ApplicationProvider.getApplicationContext(), SavedStateHandle(), lookup)
        compose.setContent {
            TripTheme("Dark") {
                val density = LocalDensity.current
                val keyboardNow = WindowInsets.ime.getBottom(density) > 0
                SideEffect { keyboardVisible = keyboardNow }
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    Scaffold(
                        topBar = { TopAppBar(title = { Text("Trip Companion") }) },
                        bottomBar = { NavigationBar { Text("My trips · Saved places · Map") } },
                    ) { padding ->
                        MapLayout(model, places, null, { id, label, address -> saved += Triple(id, label, address) }, Modifier.padding(padding).consumeWindowInsets(padding)) { modifier, safePadding ->
                            Surface(modifier, color = MaterialTheme.colorScheme.secondaryContainer) {
                                Box(Modifier.fillMaxSize().padding(safePadding).testTag("map-safe-content")) { Text("Controlled map renderer") }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @After fun tearDown() {
        try { compose.runOnIdle { model.clearMap() } }
        finally {
            device.executeShellCommand("wm size reset")
            device.executeShellCommand("wm density reset")
            if (previousKeyboardSetting in listOf("0", "1"))
                device.executeShellCommand("settings put secure show_ime_with_hard_keyboard $previousKeyboardSetting")
            else device.executeShellCommand("settings delete secure show_ime_with_hard_keyboard")
        }
    }

    private fun searchForBari(useKeyboard: Boolean = true) {
        compose.onNodeWithTag("map-search").performClick().performTextInput("Bari")
        compose.waitUntil(10_000) { keyboardVisible }
        assertTrue(lookup.searches.isEmpty())
        if (useKeyboard) compose.onNodeWithTag("map-search").performImeAction()
        else compose.onNodeWithContentDescription("Search places").performClick()
        compose.waitUntil(10_000) { !keyboardVisible }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("map-results").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun selectPlace() {
        searchForBari()
        compose.onNodeWithText("Luz restaurant").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("map-save").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun searchResultsOverlayTheMapAndSelectionKeepsSaveVisibleWithUniformPadding() {
        searchForBari(useKeyboard = false)
        // Compare sibling bounds in the same layout, rather than an earlier IME/window size.
        val searchRoot = compose.onNodeWithTag("map-layout").fetchSemanticsNode().boundsInRoot
        val controls = compose.onNodeWithTag("map-controls").fetchSemanticsNode().boundsInRoot
        val searchMap = compose.onNodeWithTag("map-viewport").fetchSemanticsNode().boundsInRoot
        assertEquals(searchRoot.height - controls.height, searchMap.height, 1f)
        assertEquals(controls.bottom, searchMap.top, 1f)
        compose.onNodeWithText("Luz restaurant").performClick()
        compose.onNodeWithTag("map-results").assertDoesNotExist()
        compose.onNodeWithTag("map-save").assertIsDisplayed()
        compose.onNodeWithTag("map-search").assertIsNotFocused()
        val search = compose.onNodeWithTag("map-search").fetchSemanticsNode().boundsInRoot
        val selectedControls = compose.onNodeWithTag("map-controls").fetchSemanticsNode().boundsInRoot
        val savedAction = compose.onNodeWithTag("map-saved-places").fetchSemanticsNode().boundsInRoot
        assertEquals(48f, search.height, 1f) // Fixture uses 160 dpi and the standard text scale.
        assertEquals(selectedControls.center.x, savedAction.center.x, 1f)
        val root = compose.onNodeWithTag("map-layout").fetchSemanticsNode().boundsInRoot
        val map = compose.onNodeWithTag("map-viewport").fetchSemanticsNode().boundsInRoot
        val surround = compose.onNodeWithTag("map-card-surround").fetchSemanticsNode().boundsInRoot
        val card = compose.onNodeWithTag("map-place-card").fetchSemanticsNode().boundsInRoot
        val safeMap = compose.onNodeWithTag("map-safe-content").fetchSemanticsNode().boundsInRoot
        assertTrue("Visible map should occupy most of the selected-place screen", safeMap.height > root.height * 0.5f)
        assertEquals(safeMap.bottom, surround.top, 1f)
        assertEquals(card.left - surround.left, card.top - surround.top, 1f)
        assertEquals(12f, root.bottom - map.bottom, 1f)
        assertEquals(card.center.x, compose.onNodeWithTag("map-save").fetchSemanticsNode().boundsInRoot.center.x, 1f)
        assertTrue(saved.isEmpty())
        compose.onNodeWithTag("map-save").performClick()
        assertEquals(listOf(Triple("luz-id", "Luz", "Via Giuseppe Re David, 32, 70126 Bari BA, Italy")), saved)
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

    @Test fun draggingTheSheetLoadsDetailsOnceAndBackAndCloseKeepMapUsable() {
        selectPlace()
        assertTrue(lookup.richCalls.isEmpty())
        compose.onNodeWithTag("map-card-surround").performTouchInput {
            swipe(Offset(center.x, 28f), Offset(center.x, -200f), durationMillis = 300)
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("map-place-details").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("★ 4.5 · 12 ratings").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Monday: 09:00–17:00").performScrollTo().assertIsDisplayed()
        assertEquals(listOf("luz-id"), lookup.richCalls)
        device.pressBack()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("map-place-details").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("map-save").assertIsDisplayed()
        compose.onNodeWithContentDescription("Expand place details").performClick()
        assertEquals(listOf("luz-id"), lookup.richCalls)
        compose.onNodeWithContentDescription("Close place").performClick()
        compose.onNodeWithTag("map-save").assertDoesNotExist()
        compose.onNodeWithTag("map-search").assertIsDisplayed()
        assertTrue(saved.isEmpty())
    }

    @Test fun shortAndWideWindowsKeepSaveUsableAndSavedPlacesDoNotOfferDuplicates() {
        selectPlace()
        device.executeShellCommand("wm size 400x520")
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Map options").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("map-save").assertIsDisplayed()
        val viewport = compose.onNodeWithTag("map-viewport").fetchSemanticsNode().boundsInRoot
        val root = compose.onNodeWithTag("map-layout").fetchSemanticsNode().boundsInRoot
        assertTrue("Short-window map collapsed: root=$root, map=$viewport", viewport.height > 40f)
        compose.runOnIdle { fontScale = 1.5f }
        compose.onNodeWithTag("map-save").assertIsDisplayed()
        assertTrue("Save should retain a 48 dp target with large text in a short window",
            compose.onNodeWithTag("map-save").fetchSemanticsNode().boundsInRoot.height >= 48f)
        compose.runOnIdle { fontScale = 1f }
        compose.onNodeWithContentDescription("Map options").performClick()
        compose.onNodeWithText("Expand map").performClick()
        compose.onNodeWithTag("map-save").assertIsDisplayed()
        compose.onNodeWithContentDescription("Exit expanded map").performClick()
        device.executeShellCommand("wm size 1000x900")
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Map options").fetchSemanticsNodes().isEmpty() }
        compose.runOnIdle { fontScale = 1.5f }
        compose.onNodeWithTag("map-save").assertIsDisplayed()
        compose.onNodeWithTag("map-save").performClick()
        assertEquals(listOf(Triple("luz-id", "Luz", "Via Giuseppe Re David, 32, 70126 Bari BA, Italy")), saved)
        compose.runOnIdle { places = listOf(Place(name = "My Luz", googlePlaceId = "luz-id")) }
        compose.onNodeWithText("Saved as My Luz").assertIsDisplayed()
        compose.onNodeWithTag("map-save").assertDoesNotExist()
    }

    private class FakePlaces : PlaceLookup {
        val searches = mutableListOf<String>()
        val richCalls = mutableListOf<String>()
        override suspend fun moreDetails(id: String): PlaceDetails {
            richCalls += id
            return PlaceDetails(rating = 4.5, ratingCount = 12, openNow = true, hours = listOf("Monday: 09:00–17:00"))
        }
        override suspend fun search(query: String): List<PlaceSuggestion> {
            searches += query
            return listOf(PlaceSuggestion("luz-id", "Luz restaurant", "Bari"), PlaceSuggestion("museum-id", "Museum", "Bari"))
        }
        override suspend fun details(id: String, fromSearch: Boolean) = MapLocation(
            id, "Luz", "Via Giuseppe Re David, 32, 70126 Bari BA, Italy", 41.1, 16.9,
        )
    }
}
