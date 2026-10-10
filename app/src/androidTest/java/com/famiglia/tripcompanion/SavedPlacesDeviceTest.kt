package com.famiglia.tripcompanion

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.famiglia.tripcompanion.data.*
import com.famiglia.tripcompanion.maps.*
import com.famiglia.tripcompanion.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercises the actual app navigation and saved-place UI with a controlled provider/renderer. */
@RunWith(AndroidJUnit4::class)
class SavedPlacesDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app get() = compose.activity.application as TripApplication
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val lookup = FakePlaces()
    private var previousLookup: PlaceLookup? = null
    private var linkedId = 0L
    private var manualId = 0L
    private var fontScale by mutableFloatStateOf(1f)
    private lateinit var ownPhoto: File

    @Before fun setUp() {
        device.executeShellCommand("wm density 160")
        device.executeShellCommand("wm size 400x900")
        previousLookup = app.placeLookupOverride
        app.placeLookupOverride = lookup
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        ownPhoto = File(app.cacheDir, "saved-place-test.png")
        ownPhoto.outputStream().use { Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        runBlocking(Dispatchers.IO) {
            app.database.clearAllTables()
            linkedId = app.repository.save(Place(name = "Luz Café", category = PlaceCategory.CAFE.name, address = "Sample Street, Bari", googlePlaceId = "luz-id"))
            manualId = app.repository.save(Place(name = "Manual café", address = "Sampletown", photoUri = Uri.fromFile(ownPhoto).toString()))
        }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), LocalMapRenderer provides { modifier, padding ->
                Surface(modifier) { Box(Modifier.fillMaxSize().padding(padding).testTag("controlled-map")) { Text("Controlled map") } }
            }) {
                val model: TravelViewModel = viewModel()
                TravelApp(model)
            }
        }
        compose.onNode(hasText("Saved places") and hasClickAction() and hasAnyAncestor(hasTestTag("glass-navigation"))).performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("saved-place-$linkedId").fetchSemanticsNodes().isNotEmpty() }
    }

    @After fun tearDown() {
        app.placeLookupOverride = previousLookup
        ownPhoto.delete()
        device.executeShellCommand("wm size reset")
        device.executeShellCommand("wm density reset")
    }

    @Test fun compactRowsKeepImagesLeftAndMenusEditableAndOpenTheExactLinkedPlace() {
        val image = compose.onNodeWithTag("place-image-$linkedId").fetchSemanticsNode().boundsInRoot
        val name = compose.onNodeWithText("Luz Café").fetchSemanticsNode().boundsInRoot
        assertTrue(image.right < name.left)
        assertTrue(compose.onNodeWithTag("saved-place-$linkedId").fetchSemanticsNode().boundsInRoot.height < 160f)
        compose.onNodeWithText("The spots you don't want to forget.").assertDoesNotExist()
        compose.onNodeWithContentDescription("Save a place").assertIsDisplayed()
        compose.onNodeWithContentDescription("Options for Luz Café").performClick()
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithText("Place name").assertTextContains("Luz Café")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("place-image-$linkedId").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Saved as Luz Café").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Saved as Luz Café").assertIsDisplayed()
        compose.onNodeWithText("Trip Companion").assertDoesNotExist()
        val dock = compose.onNodeWithTag("glass-navigation").fetchSemanticsNode().boundsInRoot
        val mapFrame = compose.onNodeWithTag("map-frame").fetchSemanticsNode().boundsInRoot
        val safeMap = compose.onNodeWithTag("controlled-map").fetchSemanticsNode().boundsInRoot
        val card = compose.onNodeWithTag("map-place-card").fetchSemanticsNode().boundsInRoot
        assertTrue("Map must continue behind the floating navigation", mapFrame.bottom > dock.top)
        assertTrue("SDK controls must remain above navigation and the place card", safeMap.bottom < card.top)
        assertTrue("The card must not cover navigation", card.bottom <= dock.top)
        val toolbar = compose.onNodeWithTag("map-toolbar").fetchSemanticsNode().boundsInRoot
        assertTrue(toolbar.bottom <= compose.onNodeWithTag("map-search").fetchSemanticsNode().boundsInRoot.top)
        val themeAction = hasContentDescription("Change appearance", substring = true)
        compose.onAllNodes(themeAction).assertCountEquals(1)
        compose.onNodeWithContentDescription("Expand place details").performClick()
        compose.onNodeWithTag("map-place-details").assertIsDisplayed()
        compose.onAllNodes(themeAction).assertCountEquals(1)
        val previousTheme = compose.onNode(themeAction).fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
        compose.onNode(themeAction).performClick()
        compose.waitUntil(10_000) {
            compose.onNode(themeAction).fetchSemanticsNode().config[SemanticsProperties.ContentDescription] != previousTheme
        }
        assertEquals(listOf("luz-id"), lookup.selected)
        assertEquals(listOf("luz-id"), lookup.photos.distinct())
    }

    @Test fun unlinkedImagePrefillsSearchWithoutGuessingALocationOrUploadingAutomatically() {
        compose.onNodeWithTag("place-image-$manualId").performClick()
        compose.onNodeWithTag("map-search").assertTextContains("Manual café Sampletown")
        assertTrue(lookup.selected.isEmpty())
        assertTrue(lookup.searches.isEmpty())
        assertEquals(listOf("luz-id"), lookup.photos.distinct())
    }

    @Test fun mapActionAndGlassNavigationStayUsableAcrossThemeChangesAndLargerText() {
        compose.runOnIdle { fontScale = 1.5f }
        compose.onNode(hasText("View on map") and hasClickAction()).performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Saved as Luz Café").fetchSemanticsNodes().isNotEmpty() }
        val themeAction = hasContentDescription("Change appearance", substring = true)
        repeat(2) {
            val before = compose.onNode(themeAction).fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
            compose.onNode(themeAction).performClick()
            compose.waitUntil(10_000) { compose.onNode(themeAction).fetchSemanticsNode().config[SemanticsProperties.ContentDescription] != before }
            compose.onNodeWithTag("map-search").assertIsDisplayed()
            compose.onNodeWithContentDescription("Close place").assertIsDisplayed()
            compose.onNodeWithTag("glass-navigation").assertIsDisplayed()
        }
        compose.onNode(hasText("Saved places") and hasClickAction() and hasAnyAncestor(hasTestTag("glass-navigation"))).performClick()
        compose.onNode(hasText("View on map") and hasClickAction()).assertIsDisplayed()
        assertEquals(listOf("luz-id"), lookup.selected)
    }

    private class FakePlaces : PlaceLookup {
        val selected = mutableListOf<String>()
        val photos = mutableListOf<String>()
        val searches = mutableListOf<String>()
        override suspend fun search(query: String): List<PlaceSuggestion> { searches += query; return emptyList() }
        override suspend fun thumbnail(id: String): PlacePhoto? { photos += id; return null }
        override suspend fun details(id: String, fromSearch: Boolean): MapLocation {
            selected += id
            return MapLocation(id, "Provider place", "Bari", 41.1, 16.9)
        }
    }
}
