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
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Streetview
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.famiglia.tripcompanion.data.Place
import com.famiglia.tripcompanion.maps.*

@Composable
internal fun MapPlaceSheet(selected: MapLocation, saved: Place?, model: MapViewModel, state: MapSearchState,
    height: Dp, compact: Boolean, expanded: Boolean, bottomClearance: Dp = 0.dp, toggle: () -> Unit, save: () -> Unit,
    appearance: (@Composable () -> Unit)? = null, compactHeight: (Int) -> Unit = {}, unsave: (Place) -> Unit = {}, openFeature: (PlaceFeature) -> Unit = {}) {
    val localPhoto = saved?.photoUri.orEmpty()
    val thumbnail = rememberPlaceThumbnail(selected.id, localPhoto, model)
    val pinnedSave = !expanded && (compact || LocalDensity.current.fontScale > 1.3f)
    var toolbarHeight by remember { mutableIntStateOf(0) }
    var summaryHeight by remember { mutableIntStateOf(0) }
    val reportHeight by rememberUpdatedState(compactHeight)
    LaunchedEffect(toolbarHeight, summaryHeight, expanded) {
        if (!expanded && toolbarHeight > 0 && summaryHeight > 0)
            reportHeight(toolbarHeight + summaryHeight)
    }
    // The visible surface itself is bounded. Its bottom corners must not belong
    // to a taller, clipped sheet. The scaffold's outer anchor wrapper stays fixed.
    Surface(color = Color.Transparent, modifier = Modifier.fillMaxWidth().testTag("map-card-surround")) {
        GlassSurface(Modifier.fillMaxWidth().height(height.coerceAtLeast(1.dp)).testTag("map-place-card"),
            kind = GlassKind.Details, shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxWidth().testTag("map-place-content")) {
                    Column(Modifier.fillMaxWidth().testTag("place-details-toolbar")
                        .onSizeChanged { toolbarHeight = it.height }.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        SheetHandle()
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val compactSave = maxWidth < (if (appearance != null) 352.dp else 304.dp) || LocalDensity.current.fontScale > 1.3f
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                    GoogleMapsAttribution(Modifier.padding(4.dp))
                                }
                                if (pinnedSave) {
                                    Spacer(Modifier.width(8.dp))
                                    val action = { if (saved != null) unsave(saved) else save() }
                                    if (compactSave) PlaceSaveIcon(state.busy, saved != null, action) else PlaceSaveButton(state.busy, saved != null, action)
                                }
                                PlaceSheetControls(expanded, toggle, model::dismissSelection)
                                if (expanded) appearance?.invoke()
                            }
                        }
                    }
                    // One shared heading remains mounted; the whole summary scrolls
                    // with expanded details, including the unified place-action row.
                    Column(Modifier.weight(1f).fillMaxWidth()
                        .verticalScroll(rememberScrollState(), enabled = expanded || compact || LocalDensity.current.fontScale > 1.3f)
                        .then(if (expanded) Modifier.testTag("map-place-details") else Modifier)) {
                        Column(Modifier.fillMaxWidth().onSizeChanged { if (!expanded) summaryHeight = it.height }
                            .padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                PlaceImage(localPhoto, thumbnail, selected.name,
                                    Modifier.size(if (compact || LocalDensity.current.fontScale > 1.3f) 64.dp else 80.dp)
                                        .testTag("map-place-thumbnail"), selected.category)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PlaceHeading(selected, expanded, compact)
                                    saved?.let { Text(savedLabel(it, selected), style = MaterialTheme.typography.bodyLarge,
                                        maxLines = if (expanded) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis) }
                                }
                            }
                            if (!expanded && !pinnedSave) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Box(Modifier.weight(1f)) { if (localPhoto.isBlank()) PhotoCredits(thumbnail, horizontal = true) }
                                    PlaceSaveButton(state.busy, saved != null) { if (saved != null) unsave(saved) else save() }
                                }
                            } else if (localPhoto.isBlank()) PhotoCredits(thumbnail, horizontal = true)
                        }
                        if (expanded) Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            PlaceActions(selected, state.details, saved != null, state.busy,
                                streetView = { openFeature(PlaceFeature.StreetView) }) { if (saved != null) unsave(saved) else save() }
                            if (localPhoto.isNotBlank()) PlaceImage(localPhoto, null, selected.name, Modifier.fillMaxWidth().height(160.dp))
                            if (state.detailsBusy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Loading place details…") }
                            state.detailsMessage?.let { message ->
                                Text(message)
                                TextButton(onClick = { model.loadDetails() }) { Text("Retry details") }
                            }
                            state.details?.let { details ->
                                RatingLink(details) { openFeature(PlaceFeature.Reviews) }
                                details.openNow?.let { open -> Text(if (open) "Open now" else "Closed now",
                                    color = if (open) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
                                if (details.photos.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    details.photos.forEach { photo -> Column(Modifier.width(240.dp)) {
                                        PlaceImage("", photo, selected.name, Modifier.fillMaxWidth().height(192.dp)); PhotoCredits(photo)
                                    } }
                                }
                                if (details.photosUnavailable) Text("Some photos could not be loaded. Try Refresh details.", style = MaterialTheme.typography.bodySmall)
                                if (details.hours.isNotEmpty()) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Icon(Icons.Outlined.Schedule, null, Modifier.size(26.dp))
                                        Text("Opening hours", style = MaterialTheme.typography.titleMedium)
                                    }
                                    details.hours.forEach { HorizontalDivider(); Text(it) }
                                }
                                if (details.rating == null && details.openNow == null && details.hours.isEmpty() &&
                                    details.photos.isEmpty() && localPhoto.isBlank() && !details.photosUnavailable)
                                    Text("Limited details available for this place.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                PlaceAttributions(details.attributions)
                                TextButton(onClick = { model.loadDetails(force = true) }, enabled = !state.detailsBusy) { Text("Refresh details") }
                            }
                            PlaceAttributions(selected.attributions)
                            Text("Check opening hours before visiting. Google photos and details need a connection.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Spacer(Modifier.height(bottomClearance).fillMaxWidth().testTag("map-card-dock-clearance"))
            }
        }
    }
}

@Composable
private fun PlaceHeading(place: MapLocation, expanded: Boolean, compact: Boolean) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(place.name, Modifier.testTag("place-detail-name"),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
            maxLines = if (expanded) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis)
        if (place.category.isNotBlank())
            Text(place.category, style = MaterialTheme.typography.bodyLarge,
                maxLines = if (expanded) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis)
        if (place.address.isNotBlank()) Text(place.address, Modifier.testTag("place-detail-address"),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = if (expanded) Int.MAX_VALUE else if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun savedLabel(saved: Place, selected: MapLocation): String =
    if (saved.name == selected.name) "Saved place" else "Saved as ${saved.name}"

@Composable
private fun SheetHandle() {
    Box(Modifier.fillMaxWidth().height(8.dp).testTag("place-sheet-handle"), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.width(44.dp).height(5.dp).background(MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp)))
    }
}

@Composable
private fun RowScope.PlaceSheetControls(expanded: Boolean, toggle: () -> Unit, close: () -> Unit) {
    GlassIconButton(if (expanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
        if (expanded) "Collapse place details" else "Expand place details", click = toggle)
    Spacer(Modifier.width(4.dp))
    GlassIconButton(Icons.Default.Close, "Close place", click = close)
}

@Composable
private fun PlaceSaveIcon(busy: Boolean, saved: Boolean, save: () -> Unit) {
    FilledIconButton(onClick = save, enabled = !busy,
        modifier = Modifier.size(48.dp).testTag(if (saved) "map-saved-status" else "map-save"), shape = RoundedCornerShape(16.dp)) {
        Icon(if (saved) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder, if (saved) "Unsave place" else "Save place", Modifier.size(26.dp))
    }
}

@Composable
private fun PlaceSaveButton(busy: Boolean, saved: Boolean, save: () -> Unit) {
    GlassAction(if (saved) "Saved" else "Save", if (saved) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
        modifier = Modifier.testTag(if (saved) "map-saved-status" else "map-save"), enabled = !busy, emphasized = true, click = save)
}

@Composable
private fun PlaceActions(place: MapLocation, details: PlaceDetails?, saved: Boolean, busy: Boolean, streetView: () -> Unit, save: () -> Unit) {
    val context = LocalContext.current
    fun open(intent: Intent) {
        try { context.startActivity(intent) }
        catch (_: ActivityNotFoundException) { Toast.makeText(context, "No app is available for this action.", Toast.LENGTH_SHORT).show() }
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("map-place-actions"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassAction(if (saved) "Saved" else "Save", if (saved) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
            vertical = true, modifier = Modifier.testTag(if (saved) "map-saved-status" else "map-save"),
            enabled = !busy, emphasized = true, click = save)
        GlassAction("Directions", Icons.Outlined.NearMe, vertical = true, modifier = Modifier.testTag("map-directions")) { open(Intent(Intent.ACTION_VIEW, PlaceLinks.directions(place))) }
        GlassAction("Street View", Icons.Outlined.Streetview, vertical = true, modifier = Modifier.testTag("map-street-view"), click = streetView)
        GlassAction("Share", Icons.Outlined.Share, vertical = true) {
            open(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, PlaceLinks.view(place).toString()), "Share place"))
        }
        details?.phone?.takeIf(String::isNotBlank)?.let { phone ->
            GlassAction("Call", Icons.Default.Call, vertical = true) { open(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null))) }
        }
        PlaceLinks.website(details?.website)?.let { uri -> GlassAction("Website", Icons.Outlined.Language, vertical = true) { open(Intent(Intent.ACTION_VIEW, uri)) } }
    }
}
