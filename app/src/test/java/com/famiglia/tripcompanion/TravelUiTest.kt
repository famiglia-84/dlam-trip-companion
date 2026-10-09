package com.famiglia.tripcompanion

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import com.famiglia.tripcompanion.data.Trip
import com.famiglia.tripcompanion.ui.TravelViewModel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h900dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TravelUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun cleanDatabase() {
        val database = (compose.activity.application as TripApplication).database
        runBlocking(Dispatchers.IO) { database.clearAllTables() }
        compose.waitForIdle()
    }


    @Test fun foldingAndUnfoldingPreserveSelectedTripAndSection() {
        val application = compose.activity.application as TripApplication
        runBlocking { application.repository.save(Trip(destination = "Bari", startDate = "2026-10-10", endDate = "2026-10-14")) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Bari").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Bari").performClick()
        compose.onNodeWithText("Day plans").performClick()
        compose.onNodeWithTag("compact-layout").assertExists()
        RuntimeEnvironment.setQualifiers("w1000dp-h900dp")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("expanded-layout").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Add day plan").assertIsDisplayed()
        RuntimeEnvironment.setQualifiers("w400dp-h900dp")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("compact-layout").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Add day plan").assertIsDisplayed()
    }

    @Test fun viewModelRestoresSelectionAndSectionFromSavedState() {
        val app = compose.activity.application as TripApplication
        val state = SavedStateHandle(mapOf("selectedTrip" to 7L, "section" to "Day plans"))
        val model = TravelViewModel(app, state)
        assertEquals(7L, model.selectedTrip.value)
        assertEquals("Day plans", model.section.value)
        model.selectTrip(8L)
        model.selectSection("Saved places")
        assertEquals(8L, state.get<Long>("selectedTrip"))
        assertEquals("Saved places", state.get<String>("section"))
    }
}
