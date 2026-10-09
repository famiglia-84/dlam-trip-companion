package com.famiglia.tripcompanion.ui

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.famiglia.tripcompanion.data.Place
import com.famiglia.tripcompanion.maps.MapLocation
import com.famiglia.tripcompanion.maps.MapViewModel

/** Map controls stay independent of the SDK renderer so layout can be checked without live requests. */
@Composable
internal fun MapLayout(
    model: MapViewModel, places: List<Place>, scopeId: Long?, save: (String, String) -> Unit,
    modifier: Modifier = Modifier, mapSlow: Boolean = false, map: @Composable (Modifier) -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val query by model.query.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var expandedMap by rememberSaveable(scopeId) { mutableStateOf(false) }
    var showInfo by rememberSaveable(scopeId) { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    BackHandler(enabled = expandedMap) { expandedMap = false }
    fun dismissKeyboard() { keyboard?.hide(); focus.clearFocus() }
    fun search() { if (!state.busy) { dismissKeyboard(); model.search() } }
    fun savedPlaces() { dismissKeyboard(); model.loadSavedPlaces(places) }
    fun expand() { dismissKeyboard(); expandedMap = true }

    if (!model.configured) {
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).testTag("maps-unconfigured"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Google Maps", style = MaterialTheme.typography.headlineMedium)
            Text("Maps and place search need to be enabled for this build. Your trips and saved places still work offline.")
            Text("Configure a restricted Google Maps key, then install a build with Maps enabled. See the repository’s Google Maps setup guide.")
        }
        return
    }

    BoxWithConstraints(modifier.fillMaxSize().imePadding().testTag("map-layout")) {
        val shortWindow = maxHeight < 420.dp
        Column(Modifier.fillMaxSize()) {
            if (!expandedMap) {
                Column(Modifier.fillMaxWidth().testTag("map-controls").padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = query, onValueChange = model::changeQuery,
                            placeholder = { Text("Place, address or city", maxLines = 1) }, singleLine = true,
                            shape = RoundedCornerShape(28.dp),
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = { IconButton(onClick = ::search, enabled = !state.busy) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Search places") } },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { search() }),
                            modifier = Modifier.weight(1f).testTag("map-search"),
                        )
                        if (shortWindow) Box {
                            IconButton(onClick = { showOptions = true }) { Icon(Icons.Default.MoreVert, "Map options") }
                            DropdownMenu(expanded = showOptions, onDismissRequest = { showOptions = false }) {
                                DropdownMenuItem(text = { Text("Show saved places") }, enabled = !state.busy,
                                    onClick = { showOptions = false; savedPlaces() })
                                DropdownMenuItem(text = { Text("Map information") }, onClick = { showOptions = false; showInfo = true })
                                DropdownMenuItem(text = { Text("Expand map") }, onClick = { showOptions = false; expand() })
                            }
                        }
                    }
                    if (!shortWindow) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = ::savedPlaces, enabled = !state.busy) {
                            Icon(Icons.Default.BookmarkBorder, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Saved places")
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { dismissKeyboard(); showInfo = true }) { Icon(Icons.Default.Info, "Map information") }
                        IconButton(onClick = ::expand) { Icon(Icons.Default.Fullscreen, "Expand map") }
                    }
                }
            }
            val message = state.message ?: if (mapSlow) "The map has not loaded. Check your connection and Maps configuration, then reopen this screen." else null
            if (message != null) Text(message, Modifier.padding(horizontal = 12.dp, vertical = 4.dp).testTag("map-message"), style = MaterialTheme.typography.bodySmall)
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).testTag("map-viewport")) {
                val resultsHeight = minOf(220.dp, maxHeight * 0.6f)
                map(Modifier.fillMaxSize())
                if (expandedMap) Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp), shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Row {
                        IconButton(onClick = { expandedMap = false }) { Icon(Icons.Default.Search, "Show search controls") }
                        IconButton(onClick = { expandedMap = false }) { Icon(Icons.Default.FullscreenExit, "Exit expanded map") }
                    }
                }
                if (state.suggestions.isNotEmpty()) Surface(
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp).testTag("map-results"),
                    shape = RoundedCornerShape(16.dp), shadowElevation = 6.dp,
                ) {
                    Column {
                        LazyColumn(Modifier.heightIn(max = resultsHeight)) {
                            items(state.suggestions, key = { it.id }) { suggestion ->
                                TextButton(onClick = { dismissKeyboard(); model.select(suggestion.id, fromSearch = true) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(suggestion.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(suggestion.subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                        Surface(color = Color.White, modifier = Modifier.fillMaxWidth()) {
                            Image(painterResource(com.google.android.libraries.places.R.drawable.google_maps_attribution_image), "Google Maps", Modifier.padding(4.dp).height(18.dp))
                        }
                    }
                }
            }
            state.selected?.let { selected ->
                // Separate map/card bounds keep Google's logo, controls and provider content unobscured.
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth().testTag("map-card-surround")) {
                    SelectedMapPlace(selected, places.firstOrNull { it.googlePlaceId == selected.id }, scopeId,
                        compact = shortWindow, busy = state.busy,
                        save = { dismissKeyboard(); save(selected.id, state.saveLabel.ifBlank { "Saved place" }) },
                        modifier = Modifier.padding(12.dp))
                }
            }
            PlaceAttributions((state.pins + listOfNotNull(state.selected)).flatMap(MapLocation::attributions).distinct())
        }
    }
    if (showInfo) MapInformation { showInfo = false }
}

@Composable
private fun SelectedMapPlace(
    selected: MapLocation, saved: Place?, scopeId: Long?, compact: Boolean, busy: Boolean,
    save: () -> Unit, modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(scopeId, selected.id) { mutableStateOf(false) }
    val toggle: @Composable () -> Unit = {
        IconButton(onClick = { expanded = !expanded }) {
            Icon(if (expanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                if (expanded) "Collapse place details" else "Expand place details")
        }
    }
    val action: @Composable () -> Unit = {
        if (saved == null) Button(onClick = save, enabled = !busy, modifier = Modifier.testTag("map-save")) {
            Icon(Icons.Default.BookmarkBorder, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Save place link")
        } else Text("Saved as ${saved.name}", style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    Card(modifier.fillMaxWidth().testTag("map-place-card"), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(12.dp)) {
            if (compact) Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(selected.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(selected.address, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                toggle(); action()
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(selected.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    toggle()
                }
                if (!expanded) Text(selected.address, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (expanded) Column(Modifier.heightIn(max = if (compact) 80.dp else 140.dp).verticalScroll(rememberScrollState()).testTag("map-place-details"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(selected.name, style = MaterialTheme.typography.titleMedium)
                Text(selected.address, style = MaterialTheme.typography.bodySmall)
                Text("Add your own label and notes. Google details are refreshed online.", style = MaterialTheme.typography.bodySmall)
            }
            if (!compact) { Spacer(Modifier.height(8.dp)); action() }
        }
    }
}

@Composable
private fun MapInformation(dismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(onDismissRequest = dismiss, title = { Text("Map information") },
        text = {
            Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Search or tap a named place on the map, then choose Save place link. Zoom in to reveal more places. Blank map areas are not selectable.")
                Text("Google receives map requests, your search text and selected place IDs. Booking codes and private notes stay here.")
                Text("Maps and Google place details need a connection. Your saved travel records remain available offline.")
                TextButton(onClick = { uriHandler.openUri("https://policies.google.com/privacy") }) { Text("Google privacy") }
                TextButton(onClick = { uriHandler.openUri("https://maps.google.com/help/terms_maps/") }) { Text("Maps terms") }
            }
        }, confirmButton = { TextButton(onClick = dismiss) { Text("Done") } },
    )
}

@Composable
private fun PlaceAttributions(attributions: List<String>) {
    if (attributions.isNotEmpty()) Box(Modifier.fillMaxWidth().heightIn(max = 64.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        AndroidView(
            factory = { context -> TextView(context).apply { movementMethod = LinkMovementMethod.getInstance() } },
            update = { it.text = HtmlCompat.fromHtml(attributions.joinToString("<br>"), HtmlCompat.FROM_HTML_MODE_LEGACY) },
        )
    }
}
