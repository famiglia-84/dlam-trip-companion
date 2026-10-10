package com.famiglia.tripcompanion

import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.famiglia.tripcompanion.maps.*
import com.famiglia.tripcompanion.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaceFeaturesDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val lookup = FakePlaces()
    private lateinit var model: MapViewModel
    private var panoramaStatus by mutableStateOf(StreetViewStatus.Available)
    private var panoramaMounts = 0
    private var panoramaDisposals = 0
    private var panoramaDrags = 0
    private var mapMounts = 0
    private var mapDrags = 0
    private val panoramaLocations = mutableListOf<MapLocation>()

    @Before fun setUp() {
        device.executeShellCommand("wm density 160")
        device.executeShellCommand("wm size 400x900")
        model = MapViewModel(ApplicationProvider.getApplicationContext(), SavedStateHandle(), lookup)
        compose.setContent {
            TripTheme("Dark") {
                CompositionLocalProvider(LocalStreetViewRenderer provides { place, modifier, report ->
                    val next = panoramaStatus
                    LaunchedEffect(place.id) { report(next) }
                    DisposableEffect(place.id) { onDispose { panoramaDisposals++ } }
                    AndroidView(modifier = modifier.testTag("test-native-panorama"), factory = { context ->
                        panoramaMounts++; panoramaLocations += place
                        FrameLayout(context).apply {
                            setBackgroundColor(android.graphics.Color.DKGRAY)
                            setOnTouchListener { _, event -> if (event.actionMasked == MotionEvent.ACTION_MOVE) panoramaDrags++; true }
                        }
                    })
                }) {
                    Box(Modifier.fillMaxSize().systemBarsPadding()) {
                        MapLayout(model, emptyList(), null, { _, _, _ -> }, bottomOverlay = 92.dp) { modifier, _ ->
                            AndroidView(modifier = modifier, factory = { context ->
                                mapMounts++
                                FrameLayout(context).apply {
                                    setBackgroundColor(android.graphics.Color.LTGRAY)
                                    setOnTouchListener { _, event -> if (event.actionMasked == MotionEvent.ACTION_MOVE) mapDrags++; true }
                                }
                            })
                        }
                        Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(92.dp).testTag("test-floating-dock"),
                            color = MaterialTheme.colorScheme.surface) { Text("My trips · Saved places · Map") }
                    }
                }
            }
        }
        compose.waitUntil(10_000) { compose.activity.hasWindowFocus() }
        compose.runOnIdle { model.select("pizza-id") }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("map-save").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Expand place details").performClick()
        // Card content appears as soon as expansion starts; wait for its settled
        // geometry before taking the scroll-position baseline.
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("map-controls").fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodesWithTag("map-reviews").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("map-reviews").performScrollTo().assertIsDisplayed()
    }

    @After fun tearDown() {
        compose.runOnIdle { model.clearMap() }
        device.executeShellCommand("wm size reset")
        device.executeShellCommand("wm density reset")
    }

    @Test fun reviewsAreOnDemandAndBackReturnsToTheSameScrollPosition() {
        assertTrue(lookup.reviewCalls.isEmpty())
        val before = compose.onNodeWithTag("map-reviews").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("map-reviews").performClick()
        compose.onNodeWithText("Google-selected reviews").assertIsDisplayed()
        compose.onNodeWithText("Sample author").assertIsDisplayed()
        compose.onNodeWithText("2 weeks ago").assertIsDisplayed()
        assertEquals(listOf("pizza-id"), lookup.reviewCalls)
        val card = compose.onNodeWithTag("place-feature-card").fetchSemanticsNode().boundsInRoot
        val dock = compose.onNodeWithTag("test-floating-dock").fetchSemanticsNode().boundsInRoot
        assertEquals("Card extends behind the floating dock", dock.bottom, card.bottom, 1f)
        compose.onNodeWithTag("reviews-open-maps").performScrollTo().assertIsDisplayed()
        val external = compose.onNodeWithTag("reviews-open-maps").fetchSemanticsNode().boundsInRoot
        assertTrue("Last action must scroll fully above dock", external.bottom <= dock.top)
        device.pressBack()
        compose.onNodeWithTag("place-feature-card").assertDoesNotExist()
        val after = compose.onNodeWithTag("map-reviews").fetchSemanticsNode().boundsInRoot
        assertEquals(before.top, after.top, 1f)
        assertEquals("pizza-id", model.state.value.selected?.id)
        compose.onNodeWithTag("map-reviews").performClick()
        assertEquals("Reopening reuses transient reviews", listOf("pizza-id"), lookup.reviewCalls)
        compose.onNodeWithContentDescription("Close Reviews").performClick()
        assertEquals(1, mapMounts)
        assertEquals(0, panoramaMounts)
    }

    @Test fun streetViewFollowsDirectionsAndNativeGesturesNeverDragTheCard() {
        compose.onNodeWithTag("map-street-view").performScrollTo().assertIsDisplayed()
        val directions = compose.onNodeWithTag("map-directions").fetchSemanticsNode().boundsInRoot
        val street = compose.onNodeWithTag("map-street-view").fetchSemanticsNode().boundsInRoot
        assertTrue("Street View follows Directions", street.left >= directions.right)
        compose.onNodeWithTag("map-street-view").performClick()
        compose.onNodeWithTag("test-native-panorama").assertIsDisplayed()
        assertEquals(1, panoramaMounts)
        assertEquals("pizza-id", panoramaLocations.single().id)
        assertEquals(41.12, panoramaLocations.single().latitude, 0.00001)
        assertEquals(16.87, panoramaLocations.single().longitude, 0.00001)
        assertTrue(lookup.reviewCalls.isEmpty())
        val before = compose.onNodeWithTag("place-feature-card").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("test-native-panorama").performTouchInput {
            swipe(Offset(width * 0.7f, height * 0.5f), Offset(width * 0.3f, height * 0.5f), 500)
            swipe(Offset(width * 0.5f, height * 0.7f), Offset(width * 0.5f, height * 0.3f), 500)
        }
        assertTrue("Native panorama must receive physical swipes", panoramaDrags > 0)
        assertEquals(before.top, compose.onNodeWithTag("place-feature-card").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithContentDescription("Collapse Street View").performClick()
        compose.onNodeWithContentDescription("Expand Street View").assertIsDisplayed()
        compose.onNodeWithTag("feature-sheet-handle").performTouchInput {
            swipe(center, Offset(center.x, center.y + 700f), 1000)
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("place-feature-card").fetchSemanticsNodes().isEmpty() }
        assertEquals(1, panoramaDisposals)
        assertEquals("pizza-id", model.state.value.selected?.id)
        compose.onNodeWithContentDescription("Close place").performClick()
        compose.onNodeWithTag("map-frame").performTouchInput {
            swipe(Offset(width * 0.7f, height * 0.5f), Offset(width * 0.3f, height * 0.5f), 500)
        }
        assertTrue("Closing panorama restores native map input", mapDrags > 0)
        assertEquals(1, mapMounts)
    }

    @Test fun errorsEmptyReviewsAndMissingPanoramaHaveRecoveryActions() {
        lookup.reviewFailure = true
        compose.onNodeWithTag("map-reviews").performClick()
        compose.onNodeWithText("Retry reviews").assertIsDisplayed()
        assertFalse(model.state.value.reviewsMessage.orEmpty().contains("private request"))
        compose.runOnIdle { lookup.reviewFailure = false; lookup.emptyReviews = true }
        compose.onNodeWithText("Retry reviews").performClick()
        compose.onNodeWithTag("reviews-empty").assertIsDisplayed()
        compose.onNodeWithTag("reviews-open-maps").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to place details").performClick()
        compose.runOnIdle { panoramaStatus = StreetViewStatus.Unavailable }
        compose.onNodeWithTag("map-street-view").performScrollTo().performClick()
        compose.onNodeWithTag("street-view-unavailable").assertIsDisplayed()
        compose.onNodeWithTag("street-view-open-maps").assertIsDisplayed()
        device.executeShellCommand("wm size 400x500")
        compose.onNodeWithTag("street-view-open-maps").performScrollTo().assertIsDisplayed()
        val fallback = compose.onNodeWithTag("street-view-open-maps").fetchSemanticsNode().boundsInRoot
        val dock = compose.onNodeWithTag("test-floating-dock").fetchSemanticsNode().boundsInRoot
        assertTrue("Recovery action stays reachable in a short window", fallback.bottom <= dock.top)
        device.executeShellCommand("wm size 400x900")
        compose.runOnIdle { panoramaStatus = StreetViewStatus.Available }
        compose.onNodeWithText("Retry Street View").performScrollTo().performClick()
        compose.onNodeWithTag("street-view-unavailable").assertDoesNotExist()
        assertEquals(2, panoramaMounts)
        assertEquals(1, panoramaDisposals)
        device.pressBack()
        compose.onNodeWithTag("place-feature-card").assertDoesNotExist()
        assertEquals(2, panoramaDisposals)
        compose.onNodeWithTag("map-place-details").assertIsDisplayed()
    }

    private class FakePlaces : PlaceLookup {
        val reviewCalls = mutableListOf<String>()
        var reviewFailure = false
        var emptyReviews = false
        override suspend fun search(query: String) = emptyList<PlaceSuggestion>()
        override suspend fun details(id: String, fromSearch: Boolean) = MapLocation(id, "Bari-Napoli", "Bari, Italy", 41.12, 16.87, category = "Pizza restaurant")
        override suspend fun moreDetails(id: String) = PlaceDetails(rating = 4.2, ratingCount = 3972)
        override suspend fun reviews(id: String): PlaceReviews {
            reviewCalls += id
            if (reviewFailure) error("private request")
            return PlaceReviews(if (emptyReviews) emptyList() else listOf(PlaceReview("Sample author", rating = 4.0,
                relativeTime = "2 weeks ago", text = "An illustrative test review. ".repeat(30))))
        }
    }
}
