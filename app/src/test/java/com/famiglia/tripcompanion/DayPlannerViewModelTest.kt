package com.famiglia.tripcompanion

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.famiglia.tripcompanion.data.*
import com.famiglia.tripcompanion.planning.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DayPlannerViewModelTest {
    private val ai = FakeAi()
    private lateinit var db: TripDatabase
    private lateinit var repository: TripRepository
    private lateinit var model: DayPlannerViewModel
    private val trip = Trip(1, "Bari", "2026-10-10", "2026-10-12")
    private val data = TravelData(trips = listOf(trip), places = listOf(Place(2,1,"Museum"), Place(3,1,"Garden")))
    @Before fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        val app = ApplicationProvider.getApplicationContext<TripApplication>()
        db = Room.inMemoryDatabaseBuilder(app, TripDatabase::class.java).allowMainThreadQueries().build()
        repository = TripRepository(db)
        model = DayPlannerViewModel(app, SavedStateHandle(), ai, repository)
        model.open(trip)
        model.edit { it.copy(places = listOf(2,3)) }
    }
    @After fun teardown() { model.close(); db.close(); Dispatchers.resetMain() }

    @Test fun readinessAndGenerationAreExplicitAndDraftDoesNotWriteDatabase() = runTest {
        assertEquals(0, ai.checks)
        assertEquals(0, ai.generations)
        model.generate(trip, data, true); runCurrent()
        assertEquals(0, ai.generations)
        model.checkAi(); runCurrent()
        assertEquals(1, ai.checks)
        model.generate(trip, data, true); runCurrent()
        assertEquals(1, ai.generations)
        assertNotNull(model.state.value.draft)
        assertTrue(repository.data.first().plans.isEmpty())
        model.edit { it.copy(visit = "30") }
        assertNull(model.state.value.draft)
        assertEquals(1, ai.generations)
    }
    @Test fun malformedAiResponseCannotProduceDraftAndManualFallbackNeedsNoAi() = runTest {
        ai.output = "{\"order\":[2,999]}"
        model.checkAi(); runCurrent()
        model.generate(trip, data, true); runCurrent()
        assertNull(model.state.value.draft)
        assertNotNull(model.state.value.error)
        model.generate(trip, data, false); runCurrent()
        assertNotNull(model.state.value.draft)
        assertFalse(model.state.value.draft!!.usedAi)
        assertEquals(1, ai.generations)
        assertEquals(0, ai.downloads)
    }
    @Test fun downloadableReadinessDoesNotStartDownloadUntilExplicitRequest() = runTest {
        ai.available = AiAvailability.DOWNLOADABLE
        model.checkAi(); runCurrent()
        assertEquals(0, ai.downloads)
        model.download(); runCurrent()
        assertEquals(1, ai.downloads)
        assertEquals(AiAvailability.AVAILABLE, model.state.value.availability)
        assertEquals(0, ai.generations)
    }
    @Test fun bookingConflictIsRejectedBeforeCallingModel() = runTest {
        val bookings = listOf(
            Reservation(4, 1, "First", date = trip.startDate, time = "12:00", endDate = trip.startDate, endTime = "13:00"),
            Reservation(5, 1, "Second", date = trip.startDate, time = "12:30", endDate = trip.startDate, endTime = "13:30"),
        )
        model.edit { it.copy(bookings = listOf(4,5)) }
        model.checkAi(); runCurrent()
        model.generate(trip, data.copy(reservations = bookings), true); runCurrent()
        assertEquals(0, ai.generations)
        assertNull(model.state.value.draft)
        assertTrue(model.state.value.error.orEmpty().contains("overlap"))
    }
    @Test fun cancellationDiscardsLateAiResultAndPreservesNewManualDraft() = runTest {
        val barrier = CompletableDeferred<Unit>()
        ai.barrier = barrier
        model.checkAi(); runCurrent()
        model.generate(trip, data, true); runCurrent()
        assertTrue(model.state.value.busy)
        model.stop()
        assertFalse(model.state.value.busy)
        assertEquals(AiAvailability.UNKNOWN, model.state.value.availability)
        model.edit { it.copy(places = listOf(3,2)) }
        model.generate(trip, data, false); runCurrent()
        barrier.complete(Unit); runCurrent()
        assertEquals(listOf(3L,2L), model.state.value.draft!!.order)
        assertFalse(model.state.value.draft!!.usedAi)
    }
    @Test fun sdkErrorsDoNotLeakRawMessagesAndManualPlanningRemainsAvailable() = runTest {
        ai.fail = true
        model.checkAi(); runCurrent()
        assertNotNull(model.state.value.error)
        assertFalse(model.state.value.busy)
        val sdkError = com.google.mlkit.genai.common.GenAiException("PRIVATE_REQUEST_DETAILS", null, 42)
        val message = NanoClient.describeError(java.util.concurrent.ExecutionException(sdkError))
        assertTrue(message.contains("42"))
        assertFalse(message.contains("PRIVATE_REQUEST_DETAILS"))
        model.generate(trip, data, false); runCurrent()
        assertNotNull(model.state.value.draft)
    }
    private class FakeAi : OnDeviceAi {
        var checks = 0; var downloads = 0; var generations = 0
        var available = AiAvailability.AVAILABLE
        var output = "{\"order\":[2,3]}"
        var barrier: CompletableDeferred<Unit>? = null
        var fail = false
        override suspend fun check(): AiAvailability { checks++; if (fail) throw AiFailure("Unavailable; use manual planning."); return available }
        override suspend fun download(progress: (String) -> Unit): AiAvailability { downloads++; progress("Downloading…"); return AiAvailability.AVAILABLE }
        override suspend fun generate(prompt: String): String { generations++; barrier?.await(); return output }
    }
}
