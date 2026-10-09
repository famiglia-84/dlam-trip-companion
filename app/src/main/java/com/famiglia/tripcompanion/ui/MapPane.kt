package com.famiglia.tripcompanion.ui

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.famiglia.tripcompanion.data.Place
import com.famiglia.tripcompanion.maps.MapLocation
import com.famiglia.tripcompanion.maps.MapViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay

@Composable
fun MapPane(model: MapViewModel, places: List<Place>, scopeId: Long?, save: (String, String) -> Unit, modifier: Modifier = Modifier) {
    val state by model.state.collectAsStateWithLifecycle()
    val query by model.query.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(scopeId) { model.activateScope(scopeId) }
    if (!model.configured) {
        Column(modifier.fillMaxSize().padding(24.dp).testTag("maps-unconfigured"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Google Maps", style = MaterialTheme.typography.headlineMedium)
            Text("Maps and place search need to be enabled for this build. Your trips and saved places still work offline.")
            Text("Configure a restricted Google Maps key, then install a build with Maps enabled. See the repository’s Google Maps setup guide.")
        }
        return
    }
    val camera = rememberCameraPositionState()
    var mapLoaded by remember { mutableStateOf(false) }
    var mapSlow by remember { mutableStateOf(false) }
    LaunchedEffect(mapLoaded) {
        if (!mapLoaded) { delay(15_000); mapSlow = true } else mapSlow = false
    }
    LaunchedEffect(mapLoaded, state.selected, state.pins) {
        if (mapLoaded) {
            val positions = state.selected?.let { listOf(LatLng(it.latitude, it.longitude)) }
                ?: state.pins.map { LatLng(it.latitude, it.longitude) }
            if (positions.size == 1) camera.animate(CameraUpdateFactory.newLatLngZoom(positions.first(), 15f))
            else if (positions.size > 1) {
                val bounds = LatLngBounds.builder().also { builder -> positions.forEach { builder.include(it) } }.build()
                camera.animate(CameraUpdateFactory.newLatLngBounds(bounds, 64))
            }
        }
    }
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Explore with Google Maps", style = MaterialTheme.typography.titleLarge)
            Text("Search or tap a named place on the map, then choose Save place link.", style = MaterialTheme.typography.bodySmall)
            Text("Google receives map requests, your search text and selected place IDs. Booking codes and private notes stay here.", style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick = { uriHandler.openUri("https://policies.google.com/privacy") }) { Text("Google privacy") }
                TextButton(onClick = { uriHandler.openUri("https://maps.google.com/help/terms_maps/") }) { Text("Maps terms") }
            }
            OutlinedTextField(query, model::changeQuery, label = { Text("Place, address or city") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = model::search, enabled = !state.busy) { Icon(Icons.Default.Search, null); Spacer(Modifier.width(6.dp)); Text("Search") }
                OutlinedButton(onClick = { model.loadSavedPlaces(places) }, enabled = !state.busy) { Text("Show saved places") }
            }
            state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (mapSlow) Text("The map has not loaded. Check your connection and the Maps key restrictions, then reopen this screen.", style = MaterialTheme.typography.bodySmall)
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.suggestions.isNotEmpty()) {
                LazyColumn(Modifier.heightIn(max = 160.dp)) {
                    items(state.suggestions, key = { it.id }) { suggestion ->
                        TextButton(onClick = { model.select(suggestion.id, fromSearch = true) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth()) { Text(suggestion.title); Text(suggestion.subtitle, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
                Surface(color = Color.White) {
                    Image(painterResource(com.google.android.libraries.places.R.drawable.google_maps_attribution_image), "Google Maps", Modifier.padding(4.dp).height(18.dp))
                }
            }
        }
        GoogleMap(
            modifier = Modifier.fillMaxWidth().weight(1f).testTag("google-map"), cameraPositionState = camera,
            properties = MapProperties(isMyLocationEnabled = false),
            uiSettings = MapUiSettings(myLocationButtonEnabled = false, mapToolbarEnabled = false),
            onMapLoaded = { mapLoaded = true },
            onPOIClick = { place -> model.select(place.placeId) },
        ) {
            val locations = (state.pins + listOfNotNull(state.selected)).distinctBy { it.id }
            locations.forEach { location ->
                key(location.id) {
                    Marker(state = rememberUpdatedMarkerState(position = LatLng(location.latitude, location.longitude)),
                        title = location.name, snippet = location.address,
                        onClick = { model.select(location.id); false })
                }
            }
        }
        state.selected?.let { selected ->
            Card(Modifier.fillMaxWidth().padding(12.dp)) {
                Column(Modifier.heightIn(max = 190.dp).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(selected.name, style = MaterialTheme.typography.titleMedium)
                    Text(selected.address, style = MaterialTheme.typography.bodySmall)
                    val savedPlace = places.firstOrNull { it.googlePlaceId == selected.id }
                    if (savedPlace != null) Text("Saved as ${savedPlace.name}", style = MaterialTheme.typography.labelLarge)
                    else {
                        Button(onClick = { save(selected.id, state.saveLabel.ifBlank { "Saved place" }) }, enabled = !state.busy) { Text("Save place link") }
                        Text("Add your own label and notes. Google details are refreshed online.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        PlaceAttributions((state.pins + listOfNotNull(state.selected)).flatMap(MapLocation::attributions).distinct())
    }
}

@Composable
private fun PlaceAttributions(attributions: List<String>) {
    if (attributions.isNotEmpty()) AndroidView(
        factory = { context -> TextView(context).apply { movementMethod = LinkMovementMethod.getInstance() } },
        update = { it.text = HtmlCompat.fromHtml(attributions.joinToString("<br>"), HtmlCompat.FROM_HTML_MODE_LEGACY) },
        modifier = Modifier.fillMaxWidth().heightIn(max = 80.dp).padding(horizontal = 12.dp),
    )
}
