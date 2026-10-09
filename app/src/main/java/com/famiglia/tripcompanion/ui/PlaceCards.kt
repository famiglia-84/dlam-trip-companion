package com.famiglia.tripcompanion.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.famiglia.tripcompanion.data.*
import com.famiglia.tripcompanion.maps.MapViewModel

@Composable
internal fun SavedPlacesPane(places: List<Place>, trips: List<Trip>, maps: MapViewModel,
    open: (Place) -> Unit, edit: (Place) -> Unit, remove: (Place) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("All") }
    Column(Modifier.fillMaxSize().testTag("saved-places-list")) {
        MapSearchField(query, { query = it }, true, null, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), "Search places")
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf("All") + PlaceCategory.entries.map { it.label }).forEach { label ->
                FilterChip(selected = category == label, onClick = { category = label }, label = { Text(label) })
            }
        }
        val filtered = places.filter { (category == "All" || PlaceCategory.valueOf(it.category).label == category) &&
            (it.name.contains(query, true) || it.address.contains(query, true) || it.notes.contains(query, true)) }
        if (filtered.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.BookmarkBorder, null)
                Text(if (places.isEmpty()) "Save somewhere special" else "No places found", style = MaterialTheme.typography.titleMedium)
                Text(if (places.isEmpty()) "Use + to add a place, or find one on the Map tab." else "Try another search or category.")
            }
        } else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filtered, key = { it.id }) { place ->
                SavedPlaceCard(place, trips.firstOrNull { it.id == place.tripId }?.destination ?: "Unassigned", maps, { open(place) }, { edit(place) }, { remove(place) })
            }
        }
    }
}

@Composable
internal fun SavedPlaceCard(place: Place, tripLabel: String?, maps: MapViewModel, open: () -> Unit, edit: () -> Unit, remove: () -> Unit) {
    val photo = rememberPlaceThumbnail(place.googlePlaceId, place.photoUri, maps)
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().testTag("saved-place-${place.id}"), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(72.dp).clickable(onClick = open).testTag("place-image-${place.id}").semantics(mergeDescendants = true) {
                    contentDescription = if (place.googlePlaceId != null) "View ${place.name} on map" else "Find ${place.name} on map"
                }) {
                    PlaceImage(place.photoUri, photo, place.name, Modifier.fillMaxSize(), place.category)
                    Surface(Modifier.align(Alignment.BottomEnd).size(22.dp), shape = RoundedCornerShape(6.dp)) {
                        Icon(Icons.Default.Map, null, Modifier.padding(3.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(place.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(PlaceCategory.valueOf(place.category).label + (tripLabel?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelMedium)
                    if (place.address.isNotBlank()) Text(place.address, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (place.notes.isNotBlank()) Text(place.notes, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Options for ${place.name}") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(if (place.googlePlaceId != null) "View on map" else "Find on map") }, onClick = { menu = false; open() })
                        DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; edit() })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; remove() })
                    }
                }
            }
            if (place.photoUri.isBlank()) PhotoCredits(photo)
        }
    }
}
