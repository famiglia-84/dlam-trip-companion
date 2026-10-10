package com.famiglia.tripcompanion.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.famiglia.tripcompanion.data.Place
import com.famiglia.tripcompanion.maps.*

@Composable
internal fun MapPlaceSheet(selected: MapLocation, saved: Place?, model: MapViewModel, state: MapSearchState,
    height: Dp, compact: Boolean, expanded: Boolean, toggle: () -> Unit, save: () -> Unit,
    appearance: (@Composable () -> Unit)? = null) {
    val localPhoto = saved?.photoUri.orEmpty()
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth().testTag("map-card-surround")) {
        Card(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp).height(height - 12.dp).testTag("map-place-card"), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxWidth().testTag("place-details-toolbar").padding(horizontal = 12.dp, vertical = 4.dp)) {
                    SheetHandle()
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val saveSpace = if (appearance != null) 352.dp else 304.dp
                        val compactSave = maxWidth < saveSpace || LocalDensity.current.fontScale > 1.3f
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                Surface(color = Color.White, shape = RoundedCornerShape(4.dp)) {
                                    Image(painterResource(com.google.android.libraries.places.R.drawable.google_maps_attribution_image),
                                        "Google Maps", Modifier.padding(4.dp).height(18.dp))
                                }
                            }
                            if (saved == null) {
                                Spacer(Modifier.width(8.dp))
                                if (compactSave) FilledIconButton(onClick = save, enabled = !state.busy,
                                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("map-save"), shape = RoundedCornerShape(12.dp)) {
                                    Icon(Icons.Default.BookmarkBorder, "Save place")
                                } else PlaceSaveButton(state.busy, save, "Save")
                            }
                            PlaceSheetControls(expanded, toggle, model::dismissSelection)
                            if (expanded) appearance?.invoke()
                        }
                    }
                }
                // Keep one heading mounted at the same position throughout the gesture.
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState(), enabled = expanded)
                    .padding(12.dp).then(if (expanded) Modifier.testTag("map-place-details") else Modifier),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PlaceHeading(selected, expanded, compact)
                    saved?.let { Text(savedLabel(it, selected), style = MaterialTheme.typography.labelLarge) }
                    if (expanded) {
                        PlaceActions(selected, state.details)
                        if (localPhoto.isNotBlank()) PlaceImage(localPhoto, null, selected.name, Modifier.fillMaxWidth().height(160.dp))
                        if (state.detailsBusy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Loading place details…") }
                        state.detailsMessage?.let { message ->
                            Text(message)
                            TextButton(onClick = { model.loadDetails() }) { Text("Retry details") }
                        }
                        state.details?.let { details ->
                            details.rating?.let { rating -> Text("★ $rating" + (details.ratingCount?.let { " · $it ratings" } ?: ""), style = MaterialTheme.typography.titleMedium) }
                            Text(when (details.openNow) { true -> "Open now"; false -> "Closed now"; null -> "Opening status unavailable" },
                                color = if (details.openNow == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            if (details.photos.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                details.photos.forEach { photo -> Column(Modifier.width(240.dp)) {
                                    PlaceImage("", photo, selected.name, Modifier.fillMaxWidth().height(160.dp)); PhotoCredits(photo)
                                } }
                            } else if (localPhoto.isBlank() && !details.photosUnavailable) Text("No photos available.", style = MaterialTheme.typography.bodySmall)
                            if (details.photosUnavailable) Text("Some photos could not be loaded. Try Refresh details.", style = MaterialTheme.typography.bodySmall)
                            Text("Opening hours", style = MaterialTheme.typography.titleMedium)
                            if (details.hours.isEmpty()) Text("Opening hours unavailable.") else details.hours.forEach { Text(it) }
                            PlaceAttributions(details.attributions)
                            TextButton(onClick = { model.loadDetails(force = true) }, enabled = !state.detailsBusy) { Text("Refresh details") }
                        }
                        PlaceAttributions(selected.attributions)
                        Text("Check opening hours before visiting. Google photos and details need a connection.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceHeading(place: MapLocation, expanded: Boolean, compact: Boolean) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(place.name, Modifier.testTag("place-detail-name"),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp),
            maxLines = if (expanded) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis)
        if (place.category.isNotBlank())
            Text(place.category, style = MaterialTheme.typography.bodySmall,
                maxLines = if (expanded) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis)
        if (place.address.isNotBlank()) Text(place.address, Modifier.testTag("place-detail-address"),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun savedLabel(saved: Place, selected: MapLocation): String =
    if (saved.name == selected.name) "Saved place" else "Saved as ${saved.name}"

@Composable
private fun SheetHandle() {
    Box(Modifier.fillMaxWidth().height(8.dp), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.width(36.dp).height(4.dp).background(MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp)))
    }
}

@Composable
private fun RowScope.PlaceSheetControls(expanded: Boolean, toggle: () -> Unit, close: () -> Unit) {
    IconButton(onClick = toggle) { Icon(if (expanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
        if (expanded) "Collapse place details" else "Expand place details") }
    IconButton(onClick = close) { Icon(Icons.Default.Close, "Close place") }
}

@Composable
private fun PlaceSaveButton(busy: Boolean, save: () -> Unit, label: String) {
    Button(onClick = save, enabled = !busy, shape = RoundedCornerShape(12.dp), modifier = Modifier.heightIn(min = 48.dp).testTag("map-save")) {
        Icon(Icons.Default.BookmarkBorder, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(label)
    }
}

@Composable
private fun PlaceActions(place: MapLocation, details: PlaceDetails?) {
    val context = LocalContext.current
    fun open(intent: Intent) {
        try { context.startActivity(intent) }
        catch (_: ActivityNotFoundException) { Toast.makeText(context, "No app is available for this action.", Toast.LENGTH_SHORT).show() }
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = { open(Intent(Intent.ACTION_VIEW, PlaceLinks.directions(place))) }) { Text("Directions") }
        OutlinedButton(onClick = {
            open(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, PlaceLinks.view(place).toString()), "Share place"))
        }) { Text("Share") }
        details?.phone?.takeIf(String::isNotBlank)?.let { phone ->
            OutlinedButton(onClick = { open(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null))) }) { Text("Call") }
        }
        PlaceLinks.website(details?.website)?.let { uri -> OutlinedButton(onClick = { open(Intent(Intent.ACTION_VIEW, uri)) }) { Text("Website") } }
    }
}
