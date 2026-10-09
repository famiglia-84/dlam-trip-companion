package com.famiglia.tripcompanion

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.famiglia.tripcompanion.data.Place
import com.famiglia.tripcompanion.ui.PlaceEditor
import com.famiglia.tripcompanion.ui.TravelViewModel
import com.famiglia.tripcompanion.ui.TripTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real editor and local persistence, with synthetic place details and no Google lookup. */
@RunWith(AndroidJUnit4::class)
class PlaceEditorDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val app get() = compose.activity.application as TripApplication
    private var visible by mutableStateOf(true)
    private var existing by mutableStateOf<Place?>(null)
    private var suggestedAddress by mutableStateOf("32 Sample Street, Sampletown")

    @Before fun setUp() {
        device.executeShellCommand("wm density 160")
        device.executeShellCommand("wm size 400x900")
        runBlocking(Dispatchers.IO) { app.database.clearAllTables() }
        compose.setContent {
            TripTheme("Dark") {
                val model: TravelViewModel = viewModel()
                if (visible) PlaceEditor(existing, null, emptyList(), model,
                    suggestedGoogleId = "sample-place-id", suggestedName = "Sample Café",
                    suggestedAddress = suggestedAddress) { visible = false }
            }
        }
    }

    @After fun restoreDisplay() {
        device.executeShellCommand("wm size reset")
        device.executeShellCommand("wm density reset")
    }

    @Test fun prefillsRemainEditableCancelDoesNotSaveAndExistingEditsAreKept() {
        compose.onNodeWithText("Place name").assertTextContains("Sample Café")
        compose.onNodeWithText("Address").assertTextContains("32 Sample Street, Sampletown")
        compose.onNodeWithText("Cancel").performClick()
        assertTrue(runBlocking { app.repository.data.first().places.isEmpty() })
        compose.runOnIdle { visible = true }
        compose.onNodeWithText("Place name").performTextReplacement("My café")
        compose.onNodeWithText("Address").performTextReplacement("My meeting point")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(15_000) { !visible }
        val stored = runBlocking { app.repository.data.first().places.single() }
        assertEquals("My café", stored.name)
        assertEquals("My meeting point", stored.address)
        assertEquals("sample-place-id", stored.googlePlaceId)
        compose.runOnIdle { existing = stored; visible = true }
        compose.onNodeWithText("Place name").assertTextContains("My café")
        compose.onNodeWithText("Address").assertTextContains("My meeting point")
    }

    @Test fun missingProviderAddressStaysBlankAndTheConfirmedNameIsSaved() {
        compose.runOnIdle { suggestedAddress = ""; visible = false }
        compose.waitForIdle()
        compose.runOnIdle { visible = true }
        compose.onNodeWithText("Address").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(15_000) { !visible }
        val stored = runBlocking { app.repository.data.first().places.single() }
        assertEquals("Sample Café", stored.name)
        assertEquals("", stored.address)
    }
}
