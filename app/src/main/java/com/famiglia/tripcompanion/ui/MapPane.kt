package com.famiglia.tripcompanion.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.famiglia.tripcompanion.data.Place
import com.famiglia.tripcompanion.maps.MapViewModel
import com.famiglia.tripcompanion.maps.MapArea
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay

/** Controlled UI tests use the real navigation/layout without requesting Google map tiles. */
internal val LocalMapRenderer = staticCompositionLocalOf<(@Composable (Modifier, PaddingValues) -> Unit)?> { null }

@Composable
fun MapPane(model: MapViewModel, places: List<Place>, scopeId: Long?, save: (String, String, String) -> Unit, modifier: Modifier = Modifier,
    requestedPlaceId: String? = null, consumedRequest: () -> Unit = {}, appearance: (@Composable () -> Unit)? = null, bottomOverlay: Dp = 0.dp,
    unsave: (Place) -> Unit = {}) {
    val state by model.state.collectAsStateWithLifecycle()
    val renderer = LocalMapRenderer.current
    LaunchedEffect(scopeId, requestedPlaceId) {
        model.activateScope(scopeId)
        requestedPlaceId?.let { model.select(it); consumedRequest() }
    }
    if (renderer != null) {
        MapLayout(model, places, scopeId, save, modifier, appearance = appearance, bottomOverlay = bottomOverlay, unsave = unsave, map = renderer)
        return
    }
    val camera = rememberCameraPositionState()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var mapLoaded by remember { mutableStateOf(false) }
    var mapSlow by remember { mutableStateOf(false) }
    LaunchedEffect(mapLoaded, model.configured) {
        if (model.configured && !mapLoaded) { delay(15_000); mapSlow = true } else mapSlow = false
    }
    LaunchedEffect(mapLoaded, state.cameraRequest) {
        val command = state.cameraRequest
        if (mapLoaded && command != null) {
            val positions = command.locations.map { LatLng(it.latitude, it.longitude) }
            try {
                if (positions.size == 1) camera.animate(CameraUpdateFactory.newLatLngZoom(positions.first(), 15f))
                else if (positions.size > 1) {
                    val bounds = LatLngBounds.builder().also { builder -> positions.forEach { builder.include(it) } }.build()
                    camera.animate(CameraUpdateFactory.newLatLngBounds(bounds, 64))
                }
            } finally { model.cameraHandled(command.sequence) }
        }
    }
    LaunchedEffect(mapLoaded, camera) {
        if (mapLoaded) snapshotFlow { camera.isMoving to camera.position }.collect { (moving, position) ->
            if (!moving) camera.projection?.visibleRegion?.let { visible ->
                val centre = position.target
                val radius = listOf(visible.nearLeft, visible.nearRight, visible.farLeft, visible.farRight)
                    .maxOf { MapArea.distanceMeters(centre.latitude, centre.longitude, it.latitude, it.longitude) }
                model.updateViewport(MapArea(centre.latitude, centre.longitude, maxOf(1.0, radius)))
            }
        }
    }
    fun select(id: String) {
        keyboard?.hide(); focus.clearFocus()
        model.select(id)
    }
    MapLayout(model, places, scopeId, save, modifier, mapSlow, appearance, bottomOverlay, unsave) { mapModifier, padding ->
        GoogleMap(
            modifier = mapModifier.testTag("google-map"), cameraPositionState = camera,
            contentPadding = padding,
            properties = MapProperties(isMyLocationEnabled = false),
            uiSettings = MapUiSettings(myLocationButtonEnabled = false, mapToolbarEnabled = false),
            onMapLoaded = { mapLoaded = true },
            onPOIClick = { place -> select(place.placeId) },
        ) {
            val locations = (state.pins + state.nearbyPins + listOfNotNull(state.selected)).distinctBy { it.id }
            locations.forEach { location ->
                key(location.id) {
                    Marker(state = rememberUpdatedMarkerState(position = LatLng(location.latitude, location.longitude)),
                        title = location.name, snippet = location.address,
                        onClick = { select(location.id); false })
                }
            }
        }
    }
}
