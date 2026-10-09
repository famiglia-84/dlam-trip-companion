package com.famiglia.tripcompanion.planning

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.famiglia.tripcompanion.TripApplication
import com.famiglia.tripcompanion.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

data class PlannerForm(
    val tripId: Long = 0, val date: String = "", val start: String = "09:00", val end: String = "18:00",
    val visit: String = "45", val transfer: String = "15",
    val places: List<Long> = emptyList(), val bookings: List<Long> = emptyList(),
)
data class PlannerState(
    val form: PlannerForm = PlannerForm(), val availability: AiAvailability = AiAvailability.UNKNOWN,
    val busy: Boolean = false, val saving: Boolean = false, val progress: String? = null, val error: String? = null,
    val draft: DayDraft? = null, val savedPlan: Long? = null,
)

class DayPlannerViewModel(
    application: Application, private val saved: SavedStateHandle,
    private val ai: OnDeviceAi, private val repository: TripRepository,
) : AndroidViewModel(application) {
    constructor(application: Application, saved: SavedStateHandle) : this(application, saved,
        (application as TripApplication).dayAi, application.repository)
    private val _state = MutableStateFlow(PlannerState(form = PlannerForm(
        tripId = saved["plannerTrip"] ?: 0L, date = saved["plannerDate"] ?: "",
        start = saved["plannerStart"] ?: "09:00", end = saved["plannerEnd"] ?: "18:00",
        visit = saved["plannerVisit"] ?: "45", transfer = saved["plannerTransfer"] ?: "15",
        places = saved.get<LongArray>("plannerPlaces")?.toList().orEmpty(), bookings = saved.get<LongArray>("plannerBookings")?.toList().orEmpty(),
    )))
    val state = _state.asStateFlow()
    private var job: Job? = null
    @Volatile private var sequence = 0L

    fun open(trip: Trip) {
        if (_state.value.form.tripId != trip.id) {
            stop()
            _state.value = PlannerState(form = PlannerForm(tripId = trip.id, date = trip.startDate), availability = _state.value.availability)
            storeForm()
        }
    }
    fun edit(change: (PlannerForm) -> PlannerForm) {
        if (_state.value.busy) return
        _state.update { it.copy(form = change(it.form), draft = null, error = null, savedPlan = null) }
        storeForm()
    }
    private fun storeForm() {
        val f = _state.value.form
        saved["plannerTrip"] = f.tripId; saved["plannerDate"] = f.date
        saved["plannerStart"] = f.start; saved["plannerEnd"] = f.end
        saved["plannerVisit"] = f.visit; saved["plannerTransfer"] = f.transfer
        saved["plannerPlaces"] = f.places.toLongArray(); saved["plannerBookings"] = f.bookings.toLongArray()
    }
    fun checkAi() = execute("Checking on-device AI…") {
        val available = ai.check()
        coroutineContext.ensureActive()
        _state.update { it.copy(availability = available) }
    }
    fun download() {
        if (_state.value.availability != AiAvailability.DOWNLOADABLE) return
        val token = sequence + 1
        execute("Downloading model (up to 10 minutes)…") {
            val available = ai.download { text ->
                if (sequence == token) _state.update { if (it.busy) it.copy(progress = text) else it }
            }
            coroutineContext.ensureActive()
            _state.update { it.copy(availability = available) }
        }
    }
    private fun request(trip: Trip, data: TravelData): DayRequest {
        val form = _state.value.form
        require(form.tripId == trip.id) { "Choose this trip again." }
        val places = form.places.map { id -> requireNotNull(data.places.firstOrNull { it.id == id }) { "A selected place was deleted. Choose again." } }
        val bookings = form.bookings.map { id -> requireNotNull(data.reservations.firstOrNull { it.id == id }) { "A selected booking was deleted. Choose again." } }
        val request = DayRequest(trip, form.date, form.start, form.end,
            form.visit.toIntOrNull() ?: throw IllegalArgumentException("Enter a visit duration in minutes."),
            form.transfer.toIntOrNull() ?: throw IllegalArgumentException("Enter a transfer buffer in minutes."), places, bookings)
        DaySuggestions.validate(request)
        DaySuggestions.build(request, request.places.map { it.id }, false)
        val existing = data.plans.firstOrNull { it.tripId == trip.id && it.date == form.date }
        require(existing == null || data.activities.none { it.planId == existing.id }) { "This day already has activities. Choose a new or empty day; existing plans are kept." }
        return request
    }
    fun generate(trip: Trip, data: TravelData, useAi: Boolean) {
        if (_state.value.busy || (useAi && _state.value.availability != AiAvailability.AVAILABLE)) return
        _state.update { it.copy(draft = null, savedPlan = null) }
        execute(if (useAi) "Suggesting a place order locally (up to 2 minutes)…" else "Building the schedule…") {
            val request = request(trip, data)
            require(!useAi || request.places.isNotEmpty()) { "Select a saved place for AI ordering, or build without AI for bookings." }
            val started = SystemClock.elapsedRealtime()
            val order = if (useAi) DaySuggestions.parseOrder(ai.generate(DaySuggestions.prompt(request)), request) else request.places.map { it.id }
            coroutineContext.ensureActive()
            val draft = DaySuggestions.build(request, order, useAi, SystemClock.elapsedRealtime() - started)
            _state.update { it.copy(draft = draft) }
        }
    }
    fun saveDraft() {
        val draft = _state.value.draft ?: return
        execute("Saving the reviewed day…", saving = true) {
            val id = repository.saveSuggestion(draft)
            _state.update { it.copy(savedPlan = id, draft = null) }
        }
    }
    fun stop() {
        if (!_state.value.busy || _state.value.saving) return
        ++sequence
        job?.cancel()
        _state.update { it.copy(busy = false, progress = null, error = "Stopped waiting. AICore may continue a model download; recheck AI before retrying.", availability = AiAvailability.UNKNOWN) }
    }
    fun close() {
        stop()
        _state.update { it.copy(draft = null, error = null, savedPlan = null) }
    }
    private fun execute(progress: String, saving: Boolean = false, action: suspend () -> Unit) {
        if (_state.value.busy) return
        val token = ++sequence
        _state.update { it.copy(busy = true, saving = saving, progress = progress, error = null) }
        job = viewModelScope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (sequence == token) _state.update { it.copy(error = when (e) {
                    is AiFailure -> e.message
                    is IllegalArgumentException -> e.message
                    else -> "This step could not complete. Please retry or use manual planning."
                }, availability = if (e is AiFailure) AiAvailability.UNKNOWN else it.availability) }
            } finally {
                if (sequence == token) _state.update { it.copy(busy = false, saving = false, progress = null) }
            }
        }
    }
}
