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
    val reviews: PlaceReviews? = null,
    val reviewsBusy: Boolean = false,
    val reviewsMessage: String? = null,
    val nearbyCategory: NearbyCategory? = null,
    val nearbyPins: List<MapLocation> = emptyList(),
    val viewport: MapArea? = null,
    val searchedArea: MapArea? = null,
    val areaChanged: Boolean = false,
    val nearbyMessage: String? = null,
    val cameraRequest: MapCameraRequest? = null,
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
    private var reviewsRequest: Job? = null
    private var reviewsLoadedAt = 0L
    private val thumbnailLock = Mutex()
    private val thumbnails = linkedMapOf<String, Pair<Long, PlacePhoto?>>()
    private var detailsLoadedAt = 0L
    private var hasScope = false
    private var activeScope: Long? = null
    private var cameraSequence = 0L
    private var viewportMovementSequence = 0L

    fun updateViewport(area: MapArea, userMoved: Boolean = true) {
        if (userMoved) viewportMovementSequence++
        val searched = _state.value.searchedArea
        _state.value = _state.value.copy(viewport = area,
            areaChanged = if (userMoved) searched != null && area.differsFrom(searched) else _state.value.areaChanged)
    }

    fun discover(category: NearbyCategory) {
        if (_state.value.busy) return
        val area = _state.value.viewport
        if (area == null || !area.searchable) {
            _state.value = _state.value.copy(nearbyMessage = if (area == null) "Wait for the map to load, then choose a category."
                else "Zoom in to search a smaller area (up to 50 km from its centre).")
            return
        }
        val movementAtSearch = viewportMovementSequence
        resetDetails()
        _state.value = _state.value.copy(selected = null, suggestions = emptyList(), nearbyCategory = category,
            nearbyPins = emptyList(), nearbyMessage = null, cameraRequest = null)
        execute { provider ->
            val locations = provider.nearby(category, area).distinctBy { it.id }.take(20)
            kotlin.coroutines.coroutineContext.ensureActive()
            _state.value = _state.value.copy(nearbyPins = locations, searchedArea = area,
                areaChanged = viewportMovementSequence != movementAtSearch && _state.value.viewport?.differsFrom(area) == true,
                nearbyMessage = if (locations.isEmpty()) "No ${category.label.lowercase()} found here. Try another area or category."
                    else "${locations.size} nearby results · tap a pin for details")
        }
    }

    fun searchThisArea() { _state.value.nearbyCategory?.let(::discover) }

    fun clearNearby() {
        request?.cancel()
        _state.value = _state.value.copy(nearbyCategory = null, nearbyPins = emptyList(), searchedArea = null,
            areaChanged = false, nearbyMessage = null, busy = false)
    }

    fun cameraHandled(sequence: Long) {
        if (_state.value.cameraRequest?.sequence == sequence) _state.value = _state.value.copy(cameraRequest = null)
    }

    private fun focusCamera(locations: List<MapLocation>) {
        _state.value = _state.value.copy(cameraRequest = locations.takeIf { it.isNotEmpty() }?.let {
            MapCameraRequest(++cameraSequence, it.toList())
        })
    }

    fun activateScope(id: Long?) {
        if (!hasScope || activeScope != id) {
            clearMap()
            hasScope = true
            activeScope = id
        }
    }

    fun changeQuery(value: String) {
        clearNearby()
        resetDetails()
        request?.cancel()
        saved["mapQuery"] = value
        _state.value = _state.value.copy(suggestions = emptyList(), selected = null, busy = false, message = null, cameraRequest = null)
    }

    fun search() {
        clearNearby()
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
        val loaded = if (fromSearch) null else (_state.value.pins + _state.value.nearbyPins + listOfNotNull(_state.value.selected)).firstOrNull { it.id == id }
        if (loaded != null) {
            request?.cancel()
            _state.value = _state.value.copy(selected = loaded, saveLabel = saveLabel, suggestions = emptyList(), busy = false, message = null)
            focusCamera(listOf(loaded))
        } else {
            _state.value = _state.value.copy(selected = null, cameraRequest = null)
            execute {
                val location = it.details(id, fromSearch)
                kotlin.coroutines.coroutineContext.ensureActive()
                _state.value = _state.value.copy(selected = location, saveLabel = saveLabel, suggestions = emptyList())
                focusCamera(listOf(location))
            }
        }
    }

    fun loadSavedPlaces(places: List<Place>) {
        clearNearby()
        resetDetails()
        val ids = places.mapNotNull { it.googlePlaceId }.distinct().take(50)
        _state.value = _state.value.copy(selected = null, suggestions = emptyList(), pins = emptyList(), cameraRequest = null)
        execute { provider ->
            // Sequential requests keep concurrency and unexpected billing bounded.
            val pins = mutableListOf<MapLocation>()
            var failures = 0
            for (id in ids) {
                try { pins += provider.details(id) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { failures++ }
                kotlin.coroutines.coroutineContext.ensureActive()
                _state.value = _state.value.copy(pins = pins.toList())
            }
            _state.value = _state.value.copy(pins = pins, message = when {
                failures > 0 -> "Some places could not load. Check your connection and Maps configuration, then retry."
                places.count { it.googlePlaceId != null } > 50 -> "Showing the first 50 linked places. Open a trip to narrow the map."
                ids.isEmpty() -> "Search for a place and save a Google Maps link first."
                else -> null
            })
            focusCamera(pins)
        }
    }

    fun clearMap() {
        resetDetails()
        request?.cancel()
        _state.value = MapSearchState(viewport = _state.value.viewport)
    }

    fun dismissSelection() {
        request?.cancel()
        resetDetails()
        _state.value = _state.value.copy(selected = null, busy = false, message = null, cameraRequest = null)
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

    /** On-demand reviews remain in memory for this selection only, including an empty result. */
    fun loadReviews(force: Boolean = false) {
        val id = _state.value.selected?.id ?: return
        val provider = lookup ?: return
        if (_state.value.reviewsBusy || (!force && _state.value.reviews != null &&
                android.os.SystemClock.elapsedRealtime() - reviewsLoadedAt < 60_000L)) return
        _state.value = _state.value.copy(reviewsBusy = true, reviewsMessage = null)
        reviewsRequest = viewModelScope.launch {
            try {
                val result = provider.reviews(id)
                kotlin.coroutines.coroutineContext.ensureActive()
                if (_state.value.selected?.id == id) {
                    reviewsLoadedAt = android.os.SystemClock.elapsedRealtime()
                    _state.value = _state.value.copy(reviews = result)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (_state.value.selected?.id == id) _state.value = _state.value.copy(
                    reviewsMessage = "Reviews could not load. Check your connection and try again.")
            } finally {
                if (kotlin.coroutines.coroutineContext[Job]?.isActive == true && _state.value.selected?.id == id)
                    _state.value = _state.value.copy(reviewsBusy = false)
            }
        }
    }

    fun cancelReviewsLoad() {
        reviewsRequest?.cancel()
        _state.value = _state.value.copy(reviewsBusy = false)
    }

    private fun resetDetails() {
        detailRequest?.cancel()
        cancelReviewsLoad()
        reviewsLoadedAt = 0L
        detailsLoadedAt = 0L
        _state.value = _state.value.copy(details = null, detailsBusy = false, detailsMessage = null, reviews = null, reviewsBusy = false, reviewsMessage = null)
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
                kotlin.coroutines.coroutineContext.ensureActive()
                // SDK exceptions can contain request details: keep them out of the UI and logs.
                _state.value = _state.value.copy(message = "Could not load Google Places. Check your connection or Maps configuration and retry.")
            }
            finally { if (kotlin.coroutines.coroutineContext[Job]?.isActive == true) _state.value = _state.value.copy(busy = false) }
        }
    }
}
