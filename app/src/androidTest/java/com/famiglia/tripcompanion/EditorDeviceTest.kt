package com.famiglia.tripcompanion

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.famiglia.tripcompanion.data.Trip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android window/input tests: run in CI's dedicated emulator, not on a personal phone. */
@RunWith(AndroidJUnit4::class)
class EditorDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before fun setUp() {
        device.executeShellCommand("wm density 160")
        device.executeShellCommand("wm size 400x900")
        val database = (compose.activity.application as TripApplication).database
        runBlocking(Dispatchers.IO) { database.clearAllTables() }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("compact-layout").fetchSemanticsNodes().isNotEmpty() }
    }

    @After fun restoreDisplay() {
        device.executeShellCommand("wm size reset")
        device.executeShellCommand("wm density reset")
    }

    @Test fun emptyHomeOpensTripEditorAndShowsValidation() {
        compose.onNodeWithText("Your next chapter starts here").assertIsDisplayed()
        compose.onNodeWithText("New trip").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Enter a destination.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Your next chapter starts here").assertIsDisplayed()
    }

    @Test fun tripCreationShowsSavedTripAndSurvivesActivityRecreation() {
        compose.onNodeWithText("New trip").performClick()
        compose.onNodeWithText("Destination").performTextInput("Bari")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Bari").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Add reservation").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Bari").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Add reservation").assertIsDisplayed()
    }

    @Test fun editorDraftSurvivesActivityRecreation() {
        compose.onNodeWithText("New trip").performClick()
        compose.onNodeWithText("Destination").performTextInput("Florence")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Florence").assertExists()
        compose.onNodeWithText("A new adventure").assertIsDisplayed()
    }

    @Test fun narrowAndWideWindowsPreserveSelectedTripAndDayPlanSection() {
        val application = compose.activity.application as TripApplication
        runBlocking { application.repository.save(Trip(destination = "Bari", startDate = "2026-10-10", endDate = "2026-10-14")) }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Bari").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Bari").performClick()
        compose.onNodeWithText("Day plans").performClick()
        device.executeShellCommand("wm size 1000x900")
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("expanded-layout").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Add day plan").assertIsDisplayed()
        device.executeShellCommand("wm size 400x900")
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("compact-layout").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Add day plan").assertIsDisplayed()
    }
}
