package com.famiglia.tripcompanion.maps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.famiglia.tripcompanion.data.Place
import com.famiglia.tripcompanion.TripApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class MapSearchState(
    val suggestions: List<PlaceSuggestion> = emptyList(),
    val selected: MapLocation? = null,
    val saveLabel: String = "",
    val pins: List<MapLocation> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
    val details: PlaceDetails? = null,
    val detailsBusy: Boolean = false,
    val detailsMessage: String? = null,
)

class MapViewModel(
    application: Application, private val saved: SavedStateHandle, private val lookup: PlaceLookup?,
) : AndroidViewModel(application) {
    constructor(application: Application, saved: SavedStateHandle) : this(application, saved,
        (application as? TripApplication)?.placeLookupOverride ?: GooglePlaceLookup.create(application))

    val configured = lookup != null
    val query = saved.getStateFlow("mapQuery", "")
    private val _state = MutableStateFlow(MapSearchState())
    val state = _state.asStateFlow()
    private var request: Job? = null
    private var detailRequest: Job? = null
    private val thumbnailLock = Mutex()
    private val thumbnails = linkedMapOf<String, Pair<Long, PlacePhoto?>>()
    private var detailsLoadedAt = 0L
    private var hasScope = false
    private var activeScope: Long? = null

    fun activateScope(id: Long?) {
        if (!hasScope || activeScope != id) {
            clearMap()
            hasScope = true
            activeScope = id
        }
    }

    fun changeQuery(value: String) {
        resetDetails()
        request?.cancel()
        saved["mapQuery"] = value
        _state.value = _state.value.copy(suggestions = emptyList(), selected = null, busy = false, message = null)
    }

    fun search() {
        resetDetails()
        val text = query.value.trim()
        if (text.isEmpty()) {
            _state.value = _state.value.copy(message = "Enter a place, address or city.")
            return
        }
        execute {
            val results = it.search(text)
            kotlin.coroutines.coroutineContext.ensureActive()
            _state.value = _state.value.copy(suggestions = results, selected = null,
                message = if (results.isEmpty()) "No places found. Try adding a city or a fuller address." else null)
        }
    }

    fun select(id: String, fromSearch: Boolean = false) {
        if (_state.value.selected?.id != id) resetDetails()
        val saveLabel = if (fromSearch) query.value else ""
        val loaded = if (fromSearch) null else (_state.value.pins + listOfNotNull(_state.value.selected)).firstOrNull { it.id == id }
        if (loaded != null) {
            request?.cancel()
            _state.value = _state.value.copy(selected = loaded, saveLabel = saveLabel, suggestions = emptyList(), busy = false, message = null)
        } else {
            _state.value = _state.value.copy(selected = null)
            execute {
                val location = it.details(id, fromSearch)
                kotlin.coroutines.coroutineContext.ensureActive()
                _state.value = _state.value.copy(selected = location, saveLabel = saveLabel, suggestions = emptyList())
            }
        }
    }

    fun loadSavedPlaces(places: List<Place>) {
        resetDetails()
        val ids = places.mapNotNull { it.googlePlaceId }.distinct().take(50)
        _state.value = _state.value.copy(selected = null, suggestions = emptyList(), pins = emptyList())
        execute { provider ->
            // Sequential requests keep concurrency and unexpected billing bounded.
            val pins = mutableListOf<MapLocation>()
            var failures = 0
            for (id in ids) {
                try { pins += provider.details(id) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { failures++ }
                _state.value = _state.value.copy(pins = pins.toList())
            }
            _state.value = _state.value.copy(pins = pins, message = when {
                failures > 0 -> "Some places could not load. Check your connection and Maps configuration, then retry."
                places.count { it.googlePlaceId != null } > 50 -> "Showing the first 50 linked places. Open a trip to narrow the map."
                ids.isEmpty() -> "Search for a place and save a Google Maps link first."
                else -> null
            })
        }
    }

    fun clearMap() {
        resetDetails()
        request?.cancel()
        _state.value = MapSearchState()
    }

    fun dismissSelection() {
        request?.cancel()
        resetDetails()
        _state.value = _state.value.copy(selected = null, busy = false, message = null)
    }

    /** Visible rows fetch one photo, serially; references stay in bounded, short-lived memory. */
    suspend fun thumbnail(id: String): PlacePhoto? = thumbnailLock.withLock {
        val now = android.os.SystemClock.elapsedRealtime()
        thumbnails[id]?.takeIf { now - it.first < 10 * 60_000L }?.let { return@withLock it.second }
        val photo = try { lookup?.thumbnail(id) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return@withLock null } // A later visible-row attempt can retry.
        kotlin.coroutines.coroutineContext.ensureActive()
        thumbnails[id] = now to photo
        while (thumbnails.size > 20) thumbnails.remove(thumbnails.keys.first())
        photo
    }

    /** Rich fields are requested only after expansion, not on each marker tap. */
    fun loadDetails(force: Boolean = false) {
        val id = _state.value.selected?.id ?: return
        val provider = lookup ?: return
        if (_state.value.detailsBusy || (!force && _state.value.details != null && android.os.SystemClock.elapsedRealtime() - detailsLoadedAt < 60_000L)) return
        _state.value = _state.value.copy(detailsBusy = true, detailsMessage = null)
        detailRequest = viewModelScope.launch {
            try {
                val details = provider.moreDetails(id)
                kotlin.coroutines.coroutineContext.ensureActive()
                if (_state.value.selected?.id == id) {
                    detailsLoadedAt = android.os.SystemClock.elapsedRealtime()
                    _state.value = _state.value.copy(details = details)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (_state.value.selected?.id == id) _state.value = _state.value.copy(detailsMessage = "Extra details could not load. Check your connection and try again.")
            } finally {
                if (kotlin.coroutines.coroutineContext[Job]?.isActive == true && _state.value.selected?.id == id)
                    _state.value = _state.value.copy(detailsBusy = false)
            }
        }
    }

    private fun resetDetails() {
        detailRequest?.cancel()
        detailsLoadedAt = 0L
        _state.value = _state.value.copy(details = null, detailsBusy = false, detailsMessage = null)
    }

    private fun execute(action: suspend (PlaceLookup) -> Unit) {
        request?.cancel()
        val provider = lookup ?: run {
            _state.value = _state.value.copy(message = "Google Maps is not configured for this build.")
            return
        }
        _state.value = _state.value.copy(busy = true, message = null)
        request = viewModelScope.launch {
            try { action(provider) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                // SDK exceptions can contain request details: keep them out of the UI and logs.
                _state.value = _state.value.copy(message = "Could not load Google Places. Check your connection or Maps configuration and retry.")
            }
            finally { if (kotlin.coroutines.coroutineContext[Job]?.isActive == true) _state.value = _state.value.copy(busy = false) }
        }
    }
}
