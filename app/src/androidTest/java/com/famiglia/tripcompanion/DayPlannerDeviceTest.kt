package com.famiglia.tripcompanion

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.famiglia.tripcompanion.data.*
import com.famiglia.tripcompanion.planning.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Controlled inference tests verify the real UI/persistence; they do not claim Nano output quality. */
@RunWith(AndroidJUnit4::class)
class DayPlannerDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val app get() = compose.activity.application as TripApplication
    private var placeId = 0L
    private var bookingId = 0L
    private var generated = 0
    @Before fun setup() {
        device.executeShellCommand("wm density 160")
        device.executeShellCommand("wm size 400x900")
        runBlocking(Dispatchers.IO) {
            app.database.clearAllTables()
            val trip = app.repository.save(Trip(destination = "Sampletown", startDate = "2026-10-10", endDate = "2026-10-12"))
            placeId = app.repository.save(Place(tripId = trip, name = "Amber Museum"))
            bookingId = app.repository.save(Reservation(tripId = trip, title = "Lunch booking", date = "2026-10-10", time = "12:00", endDate = "2026-10-10", endTime = "13:00"))
        }
        compose.runOnIdle {
            app.dayAi = object : OnDeviceAi {
                override suspend fun check() = AiAvailability.AVAILABLE
                override suspend fun download(progress: (String) -> Unit) = AiAvailability.AVAILABLE
                override suspend fun generate(prompt: String): String { generated++; return "{\"order\":[$placeId]}" }
            }
        }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Sampletown").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Sampletown").performClick()
        compose.onNodeWithText("Day plans").performClick()
        // Selecting a section persists asynchronously; await its content before interacting.
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Suggest a day plan").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun teardown() {
        app.dayAi = NanoAi()
        device.executeShellCommand("wm size reset")
        device.executeShellCommand("wm density reset")
    }
    @Test fun reviewedDraftSurvivesRecreationAndSavesOnlyOnExplicitTap() {
        compose.onNodeWithText("Suggest a day plan").performScrollTo().performClick()
        compose.onNodeWithTag("ai-place-$placeId").performScrollTo().performClick()
        compose.onNodeWithTag("ai-booking-$bookingId").performScrollTo().performClick()
        compose.onNodeWithTag("ai-check").performScrollTo().performClick()
        compose.onNodeWithTag("ai-generate").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("ai-review").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, generated)
        assertTrue(runBlocking { app.repository.data.first().plans.isEmpty() })
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("ai-review").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("ai-save").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("ai-review").fetchSemanticsNodes().isEmpty() }
        val stored = runBlocking { app.repository.data.first() }
        assertEquals(1, stored.plans.size)
        assertEquals(listOf("Amber Museum", "Lunch booking"), stored.activities.map { it.title })
        assertEquals("12:00", stored.activities.last().time)
        compose.onNodeWithText("Suggest a day plan").performScrollTo().performClick()
        compose.onNodeWithText("Build without AI").performScrollTo().performClick()
        compose.onNodeWithTag("ai-error").performScrollTo().assertTextContains("already has activities", substring = true)
        assertEquals(2, runBlocking { app.repository.data.first().activities.size })
    }
    @Test fun dismissingDraftLeavesDayUnsavedAndManualFallbackWorks() {
        compose.onNodeWithText("Suggest a day plan").performScrollTo().performClick()
        compose.onNodeWithTag("ai-place-$placeId").performScrollTo().performClick()
        compose.onNodeWithText("Build without AI").performScrollTo().performClick()
        compose.onNodeWithTag("ai-review").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Close").performScrollTo().performClick()
        assertEquals(0, generated)
        assertTrue(runBlocking { app.repository.data.first().plans.isEmpty() })
    }
    @Test fun actualSdkReadinessCompletesAndManualPlanningRemainsUsable() {
        compose.runOnIdle { app.dayAi = NanoAi() }
        compose.onNodeWithText("Suggest a day plan").performScrollTo().performClick()
        compose.onNodeWithTag("ai-check").performScrollTo().performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasTestTag("ai-check") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("ai-place-$placeId").performScrollTo().performClick()
        compose.onNodeWithText("Build without AI").performScrollTo().performClick()
        compose.onNodeWithTag("ai-review").performScrollTo().assertIsDisplayed()
        assertEquals(0, generated)
        assertTrue(runBlocking { app.repository.data.first().plans.isEmpty() })
    }
}
