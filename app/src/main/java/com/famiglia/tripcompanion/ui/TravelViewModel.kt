package com.famiglia.tripcompanion.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.core.content.edit
import com.famiglia.tripcompanion.TripApplication
import com.famiglia.tripcompanion.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TravelViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val repository = (application as TripApplication).repository
    val data = repository.data.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TravelData())
    val selectedTrip = saved.getStateFlow<Long?>("selectedTrip", null)
    val section = saved.getStateFlow("section", "Reservations")
    private val preferences = application.getSharedPreferences("appearance", 0)
    private val _theme = MutableStateFlow(preferences.getString("theme", "System") ?: "System")
    val theme = _theme.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    fun selectTrip(id: Long?) { saved["selectedTrip"] = id }
    fun selectSection(value: String) { saved["section"] = value }
    fun cycleTheme() {
        _theme.value = when (_theme.value) { "System" -> "Light"; "Light" -> "Dark"; else -> "System" }
        preferences.edit { putString("theme", _theme.value) }
    }

    suspend fun save(trip: Trip) = repository.save(trip)
    suspend fun save(reservation: Reservation) = repository.save(reservation)
    suspend fun save(place: Place) = repository.save(place)
    suspend fun save(plan: DayPlan) = repository.save(plan)
    suspend fun save(activity: PlanActivity) = repository.save(activity)
    suspend fun move(activity: PlanActivity, delta: Int) = repository.move(activity, delta)
    suspend fun delete(trip: Trip) = repository.delete(trip)
    suspend fun delete(reservation: Reservation) = repository.delete(reservation)
    suspend fun delete(place: Place) = repository.delete(place)
    suspend fun delete(plan: DayPlan) = repository.delete(plan)
    suspend fun delete(activity: PlanActivity) = repository.delete(activity)

    fun perform(action: suspend () -> Unit) = viewModelScope.launch {
        try { action() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { _messages.emit(e.message ?: "Could not save. Please try again.") }
    }
}
