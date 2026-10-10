package com.famiglia.tripcompanion.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.famiglia.tripcompanion.maps.PanoramaLifecycle
import com.famiglia.tripcompanion.maps.PanoramaAvailability
import com.famiglia.tripcompanion.maps.MapLocation
import com.famiglia.tripcompanion.maps.PlaceLinks
import com.google.android.gms.maps.StreetViewPanoramaOptions
import com.google.android.gms.maps.StreetViewPanoramaView
import com.google.android.gms.maps.StreetViewPanorama
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.StreetViewSource
import kotlinx.coroutines.delay

internal enum class StreetViewStatus { Loading, Available, Unavailable }
/** Tests exercise the actual card and native touch boundary without downloading/billing panoramas. */
internal val LocalStreetViewRenderer = staticCompositionLocalOf<(@Composable (MapLocation, Modifier, (StreetViewStatus) -> Unit) -> Unit)?> { null }

@Composable
internal fun PlaceStreetView(place: MapLocation) {
    val renderer = LocalStreetViewRenderer.current
    val context = LocalContext.current
    var attempt by remember(place.id) { mutableIntStateOf(0) }
    var status by remember(place.id, attempt) { mutableStateOf(StreetViewStatus.Loading) }
    Column(Modifier.fillMaxSize()) {
        Text("Drag to look around. Use the card’s top handle to move or close it.",
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
        Box(Modifier.weight(1f).fillMaxWidth().testTag("street-view-viewport")) {
            key(place.id, attempt) {
                // The native view is below loading/error UI only while those states are visible.
                // No transparent full-screen Surface sits above an available panorama.
                if (renderer != null) renderer(place, Modifier.fillMaxSize()) { status = it }
                else NativeStreetView(place, Modifier.fillMaxSize()) { status = it }
            }
            if (status != StreetViewStatus.Available) Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    if (status == StreetViewStatus.Loading) {
                        CircularProgressIndicator()
                        Text("Looking for nearby Street View imagery…")
                    } else {
                        Text("Street View could not load here.", Modifier.testTag("street-view-unavailable"), style = MaterialTheme.typography.titleMedium)
                        Text("Nearby imagery may be unavailable, or there may be a connection or Maps configuration problem.")
                        TextButton(onClick = { attempt++ }) { Text("Retry Street View") }
                        GlassAction("Open Street View in Google Maps", Icons.AutoMirrored.Outlined.OpenInNew, modifier = Modifier.testTag("street-view-open-maps")) {
                            try { context.startActivity(Intent(Intent.ACTION_VIEW, PlaceLinks.streetView(place))) }
                            catch (_: ActivityNotFoundException) { Toast.makeText(context, "No app is available for this link.", Toast.LENGTH_SHORT).show() }
                        }
                    }
                }
            }
        }
    }
}

/** Use the native SDK directly: this version's Compose panorama adapter treats its
 * location callback as non-null, but missing coverage can return null. No device
 * location is used; options contain only the selected place's coordinates. */
@Composable
private fun NativeStreetView(place: MapLocation, modifier: Modifier, status: (StreetViewStatus) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val view = remember(context, place.id) {
        runCatching { StreetViewPanoramaView(context, StreetViewPanoramaOptions()
            .position(LatLng(place.latitude, place.longitude), 50, StreetViewSource.OUTDOOR)
            .panningGesturesEnabled(true).zoomGesturesEnabled(true).userNavigationEnabled(true).streetNamesEnabled(true)) }.getOrNull()
    }
    var current by remember(view) { mutableStateOf(if (view == null) StreetViewStatus.Unavailable else StreetViewStatus.Loading) }
    val report by rememberUpdatedState(status)
    LaunchedEffect(current) {
        report(current)
        if (current == StreetViewStatus.Loading) {
            // No dedicated SDK failure callback: bound waits for init/connection failures.
            delay(20_000)
            current = StreetViewStatus.Unavailable
        }
    }
    if (view == null) return
    AndroidView(factory = { view }, modifier = modifier.testTag("google-street-view"))
    DisposableEffect(view, owner) {
        var alive = true
        var panorama: StreetViewPanorama? = null
        val lifecycle = PanoramaLifecycle(owner.lifecycle) { event ->
            runCatching {
                when (event) {
                    Lifecycle.Event.ON_CREATE -> view.onCreate(null)
                    Lifecycle.Event.ON_START -> view.onStart()
                    Lifecycle.Event.ON_RESUME -> view.onResume()
                    Lifecycle.Event.ON_PAUSE -> view.onPause()
                    Lifecycle.Event.ON_STOP -> view.onStop()
                    Lifecycle.Event.ON_DESTROY -> { alive = false; view.onDestroy() }
                    Lifecycle.Event.ON_ANY -> Unit
                }
            }.onFailure { if (alive) current = StreetViewStatus.Unavailable }
        }
        runCatching {
            view.getStreetViewPanoramaAsync { ready ->
                if (alive) {
                    runCatching {
                        panorama = ready
                        PanoramaAvailability.observe(ready) { available ->
                            if (alive) current = if (available) StreetViewStatus.Available else StreetViewStatus.Unavailable
                        }
                        if (PanoramaAvailability.hasLocation(ready)) current = StreetViewStatus.Available
                    }.onFailure { if (alive) current = StreetViewStatus.Unavailable }
                }
            }
        }.onFailure { current = StreetViewStatus.Unavailable }
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        val memory = object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            override fun onLowMemory() { if (alive) runCatching { view.onLowMemory() } }
            override fun onTrimMemory(level: Int) { if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) onLowMemory() }
        }
        context.registerComponentCallbacks(memory)
        onDispose {
            alive = false
            runCatching { panorama?.setOnStreetViewPanoramaChangeListener(null) }
            context.unregisterComponentCallbacks(memory)
            lifecycle.close()
        }
    }
}
