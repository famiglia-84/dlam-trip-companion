package com.famiglia.tripcompanion

import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.widget.Button
import android.widget.FrameLayout

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
    private var bottomOverlay by mutableStateOf(0.dp)
    @Volatile private var keyboardVisible = false
    private var previousKeyboardSetting = ""
    private var themeChanges = 0
    private var nativeMapView: FrameLayout? = null
    private var mapDrags = 0
    private var pinTaps = 0
    private var zoomInTaps = 0
    private var zoomOutTaps = 0

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
                        }, bottomOverlay = bottomOverlay) { modifier, safePadding ->
                            Surface(modifier, color = MaterialTheme.colorScheme.secondaryContainer) {
                                Box(Modifier.fillMaxSize().padding(safePadding).testTag("map-safe-content")) {
                                    // Maps embeds an Android View. Semantics-only clicks on Compose
                                    // controls cannot detect an overlay blocking native map input.
                                    AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                                        FrameLayout(context).apply {
                                            nativeMapView = this
                                            var startX = 0f
                                            var dragged = false
                                            setOnTouchListener { _, event ->
                                                when (event.actionMasked) {
                                                    MotionEvent.ACTION_DOWN -> { startX = event.x; dragged = false }
                                                    MotionEvent.ACTION_MOVE -> if (kotlin.math.abs(event.x - startX) > 32f) dragged = true
                                                    MotionEvent.ACTION_UP -> if (dragged) mapDrags++
                                                }
                                                true
                                            }
                                            fun control(label: String, gravity: Int, clicked: () -> Unit) {
                                                addView(Button(context).apply {
                                                    text = label
                                                    setOnClickListener { clicked() }
                                                }, FrameLayout.LayoutParams(64, 48, gravity))
                                            }
                                            control("Pin", Gravity.CENTER) { pinTaps++; model.select("luz-id") }
                                            control("+", Gravity.BOTTOM or Gravity.RIGHT) { zoomInTaps++ }
                                            control("−", Gravity.BOTTOM or Gravity.LEFT) { zoomOutTaps++ }
                                        }
                                    })
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        try { compose.waitUntil(10_000) { compose.activity.hasWindowFocus() } }
        catch (failure: ComposeTimeoutException) {
            val windows = device.executeShellCommand("dumpsys window")
                .lineSequence().filter { it.contains("mCurrentFocus") || it.contains("mFocusedApp") ||
                    it.contains("mObscuringWindow") || it.contains("mTopFocusedDisplayId") }.joinToString("\n")
            throw AssertionError("Map test host did not gain window focus. " +
                "Lifecycle=${compose.activityRule.scenario.state}, foreground=${device.currentPackageName}, " +
                "screenOn=${device.isScreenOn}, finishing=${compose.activity.isFinishing}.\n$windows", failure)
        }
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
        awaitStableMapWindow()
    }

    private fun awaitStableMapWindow() {
        // Compose idle does not await the platform IME/window-insets animation. Start physical
        // gesture measurements only once the window has stopped resizing after keyboard dismissal.
        var previous = compose.onNodeWithTag("map-layout").fetchSemanticsNode().boundsInRoot
        var stableSince = SystemClock.uptimeMillis()
        compose.waitUntil(10_000) {
            val current = compose.onNodeWithTag("map-layout").fetchSemanticsNode().boundsInRoot
            if (current != previous || keyboardVisible) {
                previous = current
                stableSince = SystemClock.uptimeMillis()
            }
            SystemClock.uptimeMillis() - stableSince >= 500
        }
    }

    private fun touchMapControls() {
        // Compose idle can precede the embedded Android View's layout after SDK
        // padding changes. Await its real controls, rather than tapping old bounds.
        compose.waitUntil(10_000) {
            val safe = compose.onNodeWithTag("map-safe-content").fetchSemanticsNode().boundsInRoot
            var ready = false
            compose.runOnIdle {
                nativeMapView?.let { view ->
                    ready = view.isShown && !view.isLayoutRequested &&
                        kotlin.math.abs(view.width - safe.width) <= 1f &&
                        kotlin.math.abs(view.height - safe.height) <= 1f &&
                        view.getChildAt(1).right == view.width && view.getChildAt(1).bottom == view.height &&
                        view.getChildAt(2).left == 0 && view.getChildAt(2).bottom == view.height
                }
            }
            ready
        }
        compose.onNodeWithTag("map-safe-content").performTouchInput {
            swipe(Offset(48f, height * 0.25f), Offset(width - 48f, height * 0.25f), durationMillis = 400)
            click(Offset(width - 32f, height - 24f))
            click(Offset(32f, height - 24f))
        }
        compose.runOnIdle {
            assertTrue("A real drag must reach the embedded map view", mapDrags > 0)
            assertTrue("A real zoom-in tap must reach the embedded map view", zoomInTaps > 0)
            assertTrue("A real zoom-out tap must reach the embedded map view", zoomOutTaps > 0)
        }
    }

    @Test fun nativeMapReceivesDragsPinAndZoomTapsWithAndWithoutPlaceSheet() {
        touchMapControls()
        compose.runOnIdle { bottomOverlay = 80.dp; mapDrags = 0; zoomInTaps = 0; zoomOutTaps = 0 }
        // The main Map screen reserves room for its floating navigation dock.
        touchMapControls()
        compose.onNodeWithTag("map-safe-content").performTouchInput { click(center) }
        compose.runOnIdle { assertEquals("Native pin tap must select a place", 1, pinTaps) }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("map-save").fetchSemanticsNodes().isNotEmpty() }
        // Let the selected sheet and its SDK padding settle before tapping again.
        compose.waitForIdle()
        compose.runOnIdle { mapDrags = 0; zoomInTaps = 0; zoomOutTaps = 0 }
        touchMapControls()
        compose.onNodeWithContentDescription("Expand place details").performClick()
        compose.onNodeWithContentDescription("Collapse place details").performClick()
        compose.waitForIdle()
        compose.runOnIdle { mapDrags = 0; zoomInTaps = 0; zoomOutTaps = 0 }
        touchMapControls()
        compose.onNodeWithContentDescription("Close place").performClick()
        compose.waitForIdle()
        compose.runOnIdle { mapDrags = 0; zoomInTaps = 0; zoomOutTaps = 0 }
        touchMapControls()
        assertTrue("Map taps must not save records", saved.isEmpty())
    }

    @Test fun searchResultsOverlayTheMapAndSelectionKeepsABoundedContentSizedSummary() {
        searchForBari(useKeyboard = false)
        // Search results may arrive before the IME's final resize frame. Compare
        // sibling bounds only after the actual window has finished changing.
        awaitStableMapWindow()
        // Compare sibling bounds in the same layout, rather than an earlier IME/window size.
        val searchRoot = compose.onNodeWithTag("map-layout").fetchSemanticsNode().boundsInRoot
        val controls = compose.onNodeWithTag("map-controls").fetchSemanticsNode().boundsInRoot
        val searchMap = compose.onNodeWithTag("map-viewport").fetchSemanticsNode().boundsInRoot
        assertEquals(searchRoot.height, searchMap.height, 1f)
        assertEquals(searchRoot.top, searchMap.top, 1f)
        val safeBeforeSelection = compose.onNodeWithTag("map-safe-content").fetchSemanticsNode().boundsInRoot
        assertTrue("Map focus and SDK controls must sit below the floating search", safeBeforeSelection.top >= controls.bottom)
        val frame = compose.onNodeWithTag("map-frame").fetchSemanticsNode().boundsInRoot
        val searchField = compose.onNodeWithTag("map-search").fetchSemanticsNode().boundsInRoot
        assertEquals(frame.left + 8f, searchField.left, 1f)
        assertEquals(frame.right - 8f, searchField.right, 1f)
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
        awaitStableMapWindow()
        val search = compose.onNodeWithTag("map-search").fetchSemanticsNode().boundsInRoot
        assertEquals(56f, search.height, 1f) // Fixture uses 160 dpi and the standard text scale.
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
        // An absolute interaction area remains meaningful across navigation/status-bar
        // sizes; the previous screen percentage assumed the smaller, text-only card.
        assertTrue("Photo summary must leave at least 280 dp for map interaction: root=$root, safe=$safeMap", safeMap.height >= 280f)
        assertEquals(safeMap.bottom + 8f, surround.top, 1f)
        assertEquals("Card must start at the sheet edge", surround.top, card.top, 1f)
        val selectedFrame = compose.onNodeWithTag("map-frame").fetchSemanticsNode().boundsInRoot
        assertEquals(selectedFrame.left, card.left, 1f)
        assertEquals(selectedFrame.right, card.right, 1f)
        val content = compose.onNodeWithTag("map-place-content").fetchSemanticsNode().boundsInRoot
        assertTrue("Summary content should fit without a large empty panel", content.height < 280f)
        assertEquals(root.bottom, map.bottom, 1f)
        val saveButton = compose.onNodeWithTag("map-save").fetchSemanticsNode().boundsInRoot
        assertTrue("Compact Save belongs beside/below the summary", saveButton.top >=
            compose.onNodeWithTag("place-detail-name").fetchSemanticsNode().boundsInRoot.top)
        assertTrue("Content-sized card must include the Save target", saveButton.bottom <= card.bottom)
        val thumbnail = compose.onNodeWithTag("map-place-thumbnail").fetchSemanticsNode().boundsInRoot
        val heading = compose.onNodeWithTag("place-detail-name").fetchSemanticsNode().boundsInRoot
        assertTrue("Place text must sit beside the rounded thumbnail", heading.left >= thumbnail.right + 16f)
        assertEquals(80f, thumbnail.width, 1f)
        compose.onNodeWithText("Photo: Sample photographer").assertIsDisplayed()
        assertEquals("Card background must extend to the bottom", root.bottom, card.bottom, 1f)
        assertEquals("Information must retain footer clearance", root.bottom - 12f, content.bottom, 1f)
        compose.onNodeWithTag("map-lower-backdrop").assertDoesNotExist()
        assertTrue(saved.isEmpty())
        compose.onNodeWithTag("map-save").performClick()
        assertEquals(listOf(Triple("luz-id", "Luz", "Via Giuseppe Re David, 32, 70126 Bari BA, Italy")), saved)
        assertEquals(listOf("Bari"), lookup.searches)
    }

    @Test fun expandingTheMapKeepsThePlaceAndSaveAndBackRestoresSearch() {
        selectPlace()
        val initialHeight = compose.onNodeWithTag("map-safe-content").fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithContentDescription("Expand map").performClick()
        compose.onNodeWithTag("map-search").assertDoesNotExist()
        compose.onNodeWithTag("map-save").assertIsDisplayed()
        compose.onNodeWithContentDescription("Change test theme").performClick()
        assertEquals(1, themeChanges)
        assertTrue(compose.onNodeWithTag("map-safe-content").fetchSemanticsNode().boundsInRoot.height > initialHeight)
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
        val rootBeforeDrag = compose.onNodeWithTag("map-layout").fetchSemanticsNode().boundsInRoot
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
        assertEquals("Window changed during drag: before=$rootBeforeDrag, after=" +
            compose.onNodeWithTag("map-layout").fetchSemanticsNode().boundsInRoot,
            viewportBeforeDrag.height, compose.onNodeWithTag("map-sheet-viewport").fetchSemanticsNode().boundsInRoot.height, 1f)
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
        assertEquals("Expanded card must completely cover the search/toolbar", root.top, card.top, 1f)
        assertEquals(root.bottom, card.bottom, 1f)
        // At this point the previous 24 dp map corner was visible outside the
        // card's 28 dp corner. Matching outlines must reveal only the outer backdrop.
        val cornerPixels = compose.onNodeWithTag("map-layout").captureToImage().toPixelMap()
        val outside = cornerPixels[1, 10]
        val corner = cornerPixels[(card.left - root.left).toInt() + 4, 10]
        assertEquals("Map must not peek through outside the expanded card corner", outside.red, corner.red, 0.02f)
        assertEquals(outside.green, corner.green, 0.02f)
        assertEquals(outside.blue, corner.blue, 0.02f)
        val toolbar = compose.onNodeWithTag("place-details-toolbar").fetchSemanticsNode().boundsInRoot
        assertTrue("Expanded toolbar should be slim", toolbar.height <= 80f)
        compose.onNodeWithContentDescription("Google Maps").assertIsDisplayed()
        val logoPixels = compose.onNodeWithTag("google-maps-attribution").captureToImage().toPixelMap()
        assertTrue("Official wordmark must not have a white background plate", logoPixels[0, 0].luminance() < 0.3f)
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
        // Save now belongs to the scrollable action row, not the pinned toolbar.
        compose.onNodeWithTag("map-save").assertIsNotDisplayed()
        assertEquals(toolbar.top, compose.onNodeWithTag("place-details-toolbar").fetchSemanticsNode().boundsInRoot.top, 1f)
        assertEquals(listOf("luz-id"), lookup.richCalls)
        assertEquals("One cached thumbnail lookup across sheet states", listOf("luz-id"), lookup.photoCalls)
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
        device.pressBack()
        compose.onNodeWithTag("map-save").assertDoesNotExist()
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
        val safe = compose.onNodeWithTag("map-safe-content").fetchSemanticsNode().boundsInRoot
        val summary = compose.onNodeWithTag("map-card-surround").fetchSemanticsNode().boundsInRoot
        assertTrue("SDK attribution must stay above the short-window card with large text", safe.bottom + 8f <= summary.top + 1f)
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

    @Test fun handleCanCollapseThenDismissAndTheNextSelectionStartsCompact() {
        selectPlace()
        val compact = compose.onNodeWithTag("map-card-surround").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("Expand place details").performClick()
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val handle = compose.onNodeWithTag("place-sheet-handle").fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput {
            swipe(Offset(handle.center.x - root.left, handle.center.y - root.top),
                Offset(handle.center.x - root.left, compact.top + 8f - root.top), durationMillis = 900)
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("map-place-details").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("Expand place details").assertIsDisplayed()
        val collapsedHandle = compose.onNodeWithTag("place-sheet-handle").fetchSemanticsNode().boundsInRoot
        val mapRoot = compose.onNodeWithTag("map-layout").fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput {
            swipe(Offset(collapsedHandle.center.x - root.left, collapsedHandle.center.y - root.top),
                Offset(collapsedHandle.center.x - root.left, mapRoot.bottom - root.top - 4f), durationMillis = 700)
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("map-place-card").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("map-search").assertIsDisplayed()
        touchMapControls()
        assertTrue("Dismissing a selection must not modify saved places", saved.isEmpty())
        compose.runOnIdle { model.select("museum-id") }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("map-save").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Expand place details").assertIsDisplayed()
        compose.onNodeWithTag("map-place-details").assertDoesNotExist()
        compose.onNodeWithContentDescription("Expand place details").performClick()
        val expandedHandle = compose.onNodeWithTag("place-sheet-handle").fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput {
            swipe(Offset(expandedHandle.center.x - root.left, expandedHandle.center.y - root.top),
                Offset(expandedHandle.center.x - root.left, mapRoot.bottom - root.top - 4f), durationMillis = 700)
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("map-place-card").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("map-search").assertIsDisplayed()
    }

    @Test fun placesWithoutProviderDetailsShowOneQuietMessageInsteadOfEmptySections() {
        lookup.limitedDetails = true
        selectPlace()
        compose.onNodeWithContentDescription("Expand place details").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Limited details available for this place.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Limited details available for this place.").assertIsDisplayed()
        compose.onNodeWithText("Opening status unavailable").assertDoesNotExist()
        compose.onNodeWithText("Opening hours unavailable.").assertDoesNotExist()
        compose.onNodeWithText("No photos available.").assertDoesNotExist()
        compose.onNodeWithText("Opening hours").assertDoesNotExist()
        compose.onNodeWithText("Refresh details").assertIsDisplayed()
    }

    @Test fun expandedSaveUsesTheSameActionRowAndSelectedNameAndAddress() {
        selectPlace()
        compose.onNodeWithContentDescription("Expand place details").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("★ 4.5 · 12 ratings").fetchSemanticsNodes().isNotEmpty() }
        val saveTile = compose.onNodeWithTag("map-save").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val directionsTile = compose.onNodeWithTag("map-directions").fetchSemanticsNode().boundsInRoot
        val toolbar = compose.onNodeWithTag("place-details-toolbar").fetchSemanticsNode().boundsInRoot
        assertEquals("Save and Directions must have equal tile heights", directionsTile.height, saveTile.height, 1f)
        assertEquals("Save must share the action-row baseline", directionsTile.top, saveTile.top, 1f)
        assertTrue("Save must belong to the body rather than the toolbar", saveTile.top >= toolbar.bottom)
        compose.onAllNodesWithTag("map-save").assertCountEquals(1)
        compose.onNodeWithTag("map-save").performClick()
        assertEquals(listOf(Triple("luz-id", "Luz", "Via Giuseppe Re David, 32, 70126 Bari BA, Italy")), saved)
    }

    private class FakePlaces : PlaceLookup {
        var limitedDetails = false
        val searches = mutableListOf<String>()
        val richCalls = mutableListOf<String>()
        val photoCalls = mutableListOf<String>()
        override suspend fun thumbnail(id: String): PlacePhoto {
            photoCalls += id
            return PlacePhoto("content://com.famiglia.tripcompanion.test/preview",
                listOf(com.famiglia.tripcompanion.maps.PhotoCredit("Sample photographer", "https://example.com/photographer")))
        }
        override suspend fun moreDetails(id: String): PlaceDetails {
            richCalls += id
            if (limitedDetails) return PlaceDetails()
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
            id, "Luz", "Via Giuseppe Re David, 32, 70126 Bari BA, Italy", 41.1, 16.9, category = "Restaurant",
        )
    }
}
