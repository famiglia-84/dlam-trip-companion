package com.famiglia.tripcompanion.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.famiglia.tripcompanion.data.Place
import com.famiglia.tripcompanion.maps.*

@Composable
internal fun MapPlaceSheet(selected: MapLocation, saved: Place?, model: MapViewModel, state: MapSearchState,
    peek: Dp, height: Dp, compact: Boolean, expanded: Boolean, toggle: () -> Unit, save: () -> Unit) {
    val localPhoto = saved?.photoUri.orEmpty()
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth().testTag("map-card-surround")) {
        Card(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp).height(height - 12.dp).testTag("map-place-card"), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxWidth().height(peek - 12.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.fillMaxWidth().height(8.dp), contentAlignment = Alignment.TopCenter) {
                        Box(Modifier.width(36.dp).height(4.dp).background(MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp)))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(selected.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!compact && selected.category.isNotBlank()) Text(selected.category, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = toggle) { Icon(if (expanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                            if (expanded) "Collapse place details" else "Expand place details") }
                        IconButton(onClick = model::dismissSelection) { Icon(Icons.Default.Close, "Close place") }
                    }
                    if (!compact) Text(selected.address, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (saved == null) Button(onClick = save, enabled = !state.busy, shape = RoundedCornerShape(12.dp), modifier = Modifier.heightIn(min = 48.dp).testTag("map-save")) {
                            Icon(Icons.Default.BookmarkBorder, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Save place")
                        } else Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                            Text("Saved as ${saved.name}", style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (expanded) Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp).testTag("map-place-details"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(selected.name, style = MaterialTheme.typography.titleLarge)
                    Text(selected.address, style = MaterialTheme.typography.bodyMedium)
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
                    Text("Check opening hours before visiting. Google photos and details need a connection.", style = MaterialTheme.typography.bodySmall)
                } else Spacer(Modifier.weight(1f))
            }
        }
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
