package com.famiglia.tripcompanion

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contrast
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
    private var themeChanges = 0

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
                        bottomBar = { NavigationBar { Text("My trips · Saved places · Map") } },
                    ) { padding ->
                        MapLayout(model, places, null, { id, label, address -> saved += Triple(id, label, address) }, Modifier.padding(padding).consumeWindowInsets(padding), appearance = {
                            IconButton(onClick = { themeChanges++ }) { Icon(Icons.Default.Contrast, "Change test theme") }
                        }) { modifier, safePadding ->
                            Surface(modifier, color = MaterialTheme.colorScheme.secondaryContainer) {
                                Box(Modifier.fillMaxSize().padding(safePadding).testTag("map-safe-content")) { Text("Controlled map renderer") }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.waitUntil(10_000) { compose.activity.hasWindowFocus() }
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
        compose.onNodeWithTag("map-search").assertIsFocused()
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
        assertEquals(searchRoot.height - controls.height - 12f, searchMap.height, 1f)
        assertEquals(controls.bottom, searchMap.top, 1f)
        val frame = compose.onNodeWithTag("map-frame").fetchSemanticsNode().boundsInRoot
        val searchField = compose.onNodeWithTag("map-search").fetchSemanticsNode().boundsInRoot
        assertEquals(searchField.left, frame.left, 1f)
        assertEquals(searchField.right, frame.right, 1f)
        assertEquals(12f, frame.left - searchRoot.left, 1f)
        val toolbar = compose.onNodeWithTag("map-toolbar").fetchSemanticsNode().boundsInRoot
        assertTrue("Actions must sit above search", toolbar.bottom <= searchField.top)
        assertTrue(compose.onNodeWithTag("map-saved-places").fetchSemanticsNode().boundsInRoot.right <
            compose.onNodeWithContentDescription("Map information").fetchSemanticsNode().boundsInRoot.left)
        compose.onNodeWithContentDescription("Change test theme").assertIsDisplayed()
        compose.onNodeWithText("Luz restaurant").performClick()
        compose.onNodeWithTag("map-results").assertDoesNotExist()
        compose.onNodeWithTag("map-save").assertIsDisplayed()
        compose.onNodeWithTag("map-search").assertIsNotFocused()
        val search = compose.onNodeWithTag("map-search").fetchSemanticsNode().boundsInRoot
        assertEquals(48f, search.height, 1f) // Fixture uses 160 dpi and the standard text scale.
        compose.waitUntil(10_000) {
            val safe = compose.onNodeWithTag("map-safe-content").fetchSemanticsNode().boundsInRoot
            val sheet = compose.onNodeWithTag("map-card-surround").fetchSemanticsNode().boundsInRoot
            kotlin.math.abs(safe.bottom + 8f - sheet.top) <= 1f
        }
        val root = compose.onNodeWithTag("map-layout").fetchSemanticsNode().boundsInRoot
        val map = compose.onNodeWithTag("map-viewport").fetchSemanticsNode().boundsInRoot
        val surround = compose.onNodeWithTag("map-card-surround").fetchSemanticsNode().boundsInRoot
        val card = compose.onNodeWithTag("map-place-card").fetchSemanticsNode().boundsInRoot
        val safeMap = compose.onNodeWithTag("map-safe-content").fetchSemanticsNode().boundsInRoot
        assertTrue("Visible map should occupy most of the selected-place screen", safeMap.height > root.height * 0.5f)
        assertEquals(safeMap.bottom + 8f, surround.top, 1f)
        assertEquals(card.left - surround.left, card.top - surround.top, 1f)
        assertEquals(12f, root.bottom - map.bottom, 1f)
        assertTrue(compose.onNodeWithTag("map-save").fetchSemanticsNode().boundsInRoot.bottom <=
            compose.onNodeWithTag("place-detail-name").fetchSemanticsNode().boundsInRoot.top)
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
        compose.onNodeWithContentDescription("Change test theme").performClick()
        assertEquals(1, themeChanges)
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
        val viewportBeforeDrag = compose.onNodeWithTag("map-sheet-viewport").fetchSemanticsNode().boundsInRoot
        val sheetBeforeDrag = compose.onNodeWithTag("map-card-surround").fetchSemanticsNode().boundsInRoot
        val nameBefore = compose.onNodeWithTag("place-detail-name").fetchSemanticsNode().boundsInRoot
        val addressBefore = compose.onNodeWithTag("place-detail-address").fetchSemanticsNode().boundsInRoot
        val inputRoot = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val start = Offset(sheetBeforeDrag.center.x - inputRoot.left, sheetBeforeDrag.top + 28f - inputRoot.top)
        val end = Offset(start.x, viewportBeforeDrag.top + 24f - inputRoot.top)
        compose.onRoot().performTouchInput {
            down(start)
            moveTo((start + end) / 2f, delayMillis = 250)
        }
        assertEquals(viewportBeforeDrag.height,
            compose.onNodeWithTag("map-sheet-viewport").fetchSemanticsNode().boundsInRoot.height, 1f)
        assertTrue("The card must actually follow the drag", compose.onNodeWithTag("map-card-surround")
            .fetchSemanticsNode().boundsInRoot.top < sheetBeforeDrag.top - 40f)
        compose.onRoot().performTouchInput {
            moveTo(end, delayMillis = 250)
            up()
        }
        try {
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("map-place-details").fetchSemanticsNodes().isNotEmpty() }
        } catch (timeout: ComposeTimeoutException) {
            throw AssertionError("Drag did not expand: viewport=$viewportBeforeDrag, sheet=$sheetBeforeDrag\n" +
                compose.onRoot(useUnmergedTree = true).printToString(), timeout)
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("map-controls").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("map-search").assertDoesNotExist()
        compose.onAllNodesWithText("Luz").assertCountEquals(1)
        compose.onAllNodesWithText("Via Giuseppe Re David, 32, 70126 Bari BA, Italy").assertCountEquals(1)
        assertEquals(viewportBeforeDrag.height,
            compose.onNodeWithTag("map-sheet-viewport").fetchSemanticsNode().boundsInRoot.height, 1f)
        val expandedSurround = compose.onNodeWithTag("map-card-surround").fetchSemanticsNode().boundsInRoot
        val nameAfter = compose.onNodeWithTag("place-detail-name").fetchSemanticsNode().boundsInRoot
        val addressAfter = compose.onNodeWithTag("place-detail-address").fetchSemanticsNode().boundsInRoot
        assertEquals(nameBefore.height, nameAfter.height, 1f)
        assertEquals(nameBefore.left, nameAfter.left, 1f)
        assertEquals(nameBefore.top - sheetBeforeDrag.top, nameAfter.top - expandedSurround.top, 1f)
        assertEquals(addressBefore.top - nameBefore.top, addressAfter.top - nameAfter.top, 1f)
        val root = compose.onNodeWithTag("map-layout").fetchSemanticsNode().boundsInRoot
        val card = compose.onNodeWithTag("map-place-card").fetchSemanticsNode().boundsInRoot
        assertEquals(root.top + 12f, card.top, 1f)
        assertEquals(root.bottom - 12f, card.bottom, 1f)
        val toolbar = compose.onNodeWithTag("place-details-toolbar").fetchSemanticsNode().boundsInRoot
        assertTrue("Expanded toolbar should be slim", toolbar.height <= 80f)
        compose.onNodeWithContentDescription("Google Maps").assertIsDisplayed()
        compose.onNodeWithContentDescription("Change test theme").performClick()
        assertEquals(1, themeChanges)
        compose.onNodeWithText("★ 4.5 · 12 ratings").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Monday: 07:00–09:00, 11:00–14:00, 16:00–18:00, 20:00–22:00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Refresh details").performScrollTo().assertIsDisplayed()
        // Scroll the actual body to its end, rather than just bringing one lower action into view.
        compose.onRoot().performTouchInput {
            swipe(Offset(card.center.x - inputRoot.left, card.bottom - 40f - inputRoot.top),
                Offset(card.center.x - inputRoot.left, toolbar.bottom + 24f - inputRoot.top), durationMillis = 500)
        }
        try {
            compose.onNodeWithTag("place-detail-name").assertIsNotDisplayed()
            compose.onNodeWithTag("place-detail-address").assertIsNotDisplayed()
        } catch (failure: AssertionError) {
            val body = compose.onNodeWithTag("map-place-details").fetchSemanticsNode()
            throw AssertionError("Heading remains visible after body swipe: card=$card, toolbar=$toolbar, " +
                "body=${body.boundsInRoot}, name=${compose.onNodeWithTag("place-detail-name").fetchSemanticsNode().boundsInRoot}\n" +
                compose.onRoot(useUnmergedTree = true).printToString(), failure)
        }
        compose.onNodeWithTag("map-save").assertIsDisplayed()
        assertEquals(toolbar.top, compose.onNodeWithTag("place-details-toolbar").fetchSemanticsNode().boundsInRoot.top, 1f)
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
        // Closing the expanded view must not leave the next place expanded automatically.
        compose.runOnIdle { model.select("museum-id") }
        compose.onNodeWithTag("map-save").assertIsDisplayed()
        compose.onNodeWithTag("map-place-details").assertDoesNotExist()
        compose.onNodeWithTag("map-search").assertIsDisplayed()
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
            return PlaceDetails(photos = listOf(PlacePhoto("content://com.famiglia.tripcompanion.test/preview")), rating = 4.5, ratingCount = 12, openNow = true,
                // Multiple daily service windows exercise wrapped hours and guarantee real scroll overflow.
                hours = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
                    .map { "$it: 07:00–09:00, 11:00–14:00, 16:00–18:00, 20:00–22:00" })
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
