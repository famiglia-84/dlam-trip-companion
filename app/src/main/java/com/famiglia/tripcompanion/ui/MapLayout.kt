package com.famiglia.tripcompanion.ui

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.famiglia.tripcompanion.data.Place
import com.famiglia.tripcompanion.maps.MapLocation
import com.famiglia.tripcompanion.maps.MapViewModel

/** Map controls stay independent of the SDK renderer so layout can be checked without live requests. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun MapLayout(
    model: MapViewModel, places: List<Place>, scopeId: Long?, save: (String, String, String) -> Unit,
    modifier: Modifier = Modifier, mapSlow: Boolean = false, map: @Composable (Modifier, PaddingValues) -> Unit,
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

    val sheet = key(state.selected != null) {
        rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded)
    }
    val sheetScaffold = rememberBottomSheetScaffoldState(bottomSheetState = sheet)
    val scope = rememberCoroutineScope()
    val sheetExpanded = sheet.currentValue == SheetValue.Expanded || sheet.targetValue == SheetValue.Expanded
    // Resize after the gesture settles so changing anchors cannot reverse a drag midway.
    val detailsExpanded = state.selected != null && sheet.currentValue == SheetValue.Expanded
    BackHandler(enabled = state.selected != null && sheetExpanded) { scope.launch { sheet.partialExpand() } }
    LaunchedEffect(state.selected?.id, sheetExpanded) { if (sheetExpanded) model.loadDetails() }

    BoxWithConstraints(modifier.fillMaxSize().imePadding().testTag("map-layout")) {
        val shortWindow = maxHeight < 420.dp
        Column(Modifier.fillMaxSize()) {
            if (!expandedMap && !detailsExpanded) {
                Column(Modifier.fillMaxWidth().testTag("map-controls").padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MapSearchField(query, model::changeQuery, !state.busy, ::search, Modifier.weight(1f))
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
                    if (!shortWindow) BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        val separateActions = maxWidth >= 360.dp && LocalDensity.current.fontScale <= 1.3f
                        val actionSpace = if (separateActions) 96.dp else 48.dp
                        TextButton(onClick = ::savedPlaces, enabled = !state.busy,
                            modifier = Modifier.align(Alignment.Center).widthIn(max = (maxWidth - actionSpace * 2).coerceAtLeast(0.dp)).heightIn(min = 48.dp).testTag("map-saved-places")) {
                            Icon(Icons.Default.BookmarkBorder, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                            Text("Saved places", textAlign = TextAlign.Center)
                        }
                        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                            if (separateActions) {
                                IconButton(onClick = { dismissKeyboard(); showInfo = true }) { Icon(Icons.Default.Info, "Map information") }
                                IconButton(onClick = ::expand) { Icon(Icons.Default.Fullscreen, "Expand map") }
                            } else Box {
                                IconButton(onClick = { showOptions = true }) { Icon(Icons.Default.MoreVert, "Map options") }
                                DropdownMenu(expanded = showOptions, onDismissRequest = { showOptions = false }) {
                                    DropdownMenuItem(text = { Text("Map information") }, onClick = { showOptions = false; showInfo = true })
                                    DropdownMenuItem(text = { Text("Expand map") }, onClick = { showOptions = false; expand() })
                                }
                            }
                        }
                    }
                }
            }
            val message = state.message ?: if (mapSlow) "The map has not loaded. Check your connection and Maps configuration, then reopen this screen." else null
            if (message != null && !detailsExpanded) Text(message, Modifier.padding(horizontal = 12.dp, vertical = 4.dp).testTag("map-message"), style = MaterialTheme.typography.bodySmall)
            if (state.busy && !detailsExpanded) LinearProgressIndicator(Modifier.fillMaxWidth())

            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).testTag("map-viewport")) {
                val resultsHeight = minOf(220.dp, maxHeight * 0.6f)
                val compact = shortWindow || maxHeight < 400.dp
                val peek = if (state.selected == null) 0.dp else minOf(maxHeight,
                    if (compact) {
                        if (LocalDensity.current.fontScale > 1.3f) 184.dp else 148.dp
                    } else if (LocalDensity.current.fontScale > 1.3f) 220.dp else 188.dp)
                val sheetHeight = maxHeight
                val density = LocalDensity.current
                val offset = runCatching { sheet.requireOffset() }.getOrNull()?.takeIf { it.isFinite() }
                val safeBottom = if (state.selected == null) 0.dp else with(density) {
                    (maxHeight.toPx() - (offset ?: (maxHeight - peek).toPx())).coerceIn(0f, maxHeight.toPx()).toDp()
                }
                BottomSheetScaffold(
                    scaffoldState = sheetScaffold, sheetPeekHeight = peek, sheetDragHandle = null,
                    sheetSwipeEnabled = state.selected != null, sheetShadowElevation = 0.dp,
                    sheetContainerColor = MaterialTheme.colorScheme.background,
                    sheetContent = {
                        key(state.selected?.id) { state.selected?.let { selected ->
                            MapPlaceSheet(selected, places.firstOrNull { it.googlePlaceId == selected.id }, model, state,
                                peek, sheetHeight, compact, sheetExpanded,
                                toggle = { scope.launch { if (sheetExpanded) sheet.partialExpand() else sheet.expand() } },
                                save = { dismissKeyboard(); save(selected.id, selected.name.ifBlank { "Saved place" }, selected.address) })
                        } ?: Spacer(Modifier.height(1.dp)) }
                    },
                ) {
                    Surface(Modifier.fillMaxSize().padding(horizontal = 12.dp).testTag("map-frame"),
                        shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))) {
                        // Keep SDK controls inset from the rounded edge; the opaque expanded sheet covers the map.
                        val bottom = if (detailsExpanded && sheet.currentValue == SheetValue.Expanded) 8.dp
                            else minOf(safeBottom + 8.dp, (maxHeight - 96.dp).coerceAtLeast(8.dp))
                        map(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)), PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = bottom))
                    }
                }
                if (expandedMap && !detailsExpanded) Surface(
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
            Spacer(Modifier.fillMaxWidth().height(12.dp))
            if (!detailsExpanded) PlaceAttributions((state.pins + listOfNotNull(state.selected)).flatMap(MapLocation::attributions).distinct())
        }
    }
    if (showInfo) MapInformation { showInfo = false }
}

/** A 48 dp search field that grows for larger text instead of clipping it. */
@Composable
internal fun MapSearchField(value: String, change: (String) -> Unit, canSearch: Boolean, search: (() -> Unit)?, modifier: Modifier, hint: String = "Place, address or city") {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    BasicTextField(
        value = value, onValueChange = change, singleLine = true, interactionSource = interaction,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = if (search != null) ImeAction.Search else ImeAction.Done),
        keyboardActions = KeyboardActions(onSearch = { search?.invoke() }, onDone = { keyboard?.hide() }),
        modifier = modifier.heightIn(min = 48.dp).testTag(if (search != null) "map-search" else "places-search").semantics { contentDescription = hint },
        decorationBox = { input ->
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.background,
                border = BorderStroke(1.dp, if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)) {
                Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, Modifier.padding(start = 12.dp, end = 10.dp).size(22.dp))
                    Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        if (value.isEmpty()) Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        input()
                    }
                    if (search != null) IconButton(onClick = search, enabled = canSearch) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Search places") }
                    else if (value.isNotEmpty()) IconButton(onClick = { change("") }) { Icon(Icons.Default.Close, "Clear place search") }
                    else Spacer(Modifier.width(12.dp))
                }
            }
        },
    )
}

@Composable
private fun MapInformation(dismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(onDismissRequest = dismiss, title = { Text("Map information") },
        text = {
            Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Search or tap a named place on the map, then choose Save place. Review or edit its name and address before confirming. Zoom in to reveal more places. Blank map areas are not selectable.")
                Text("Google receives map requests, your search text and selected place IDs. Booking codes and private notes stay here.")
                Text("Drag the place card up, or use its expand button, for photos, ratings, hours and actions. Directions opens Google Maps. Tap a saved place's thumbnail to find it on this map.")
                Text("Google photos and expanded details load online and may incur Google Maps Platform charges. You can choose your own photo in the save dialog; it stays with your selected photo provider and is not uploaded to Google Maps.")
                Text("Maps and Google place details need a connection. Your saved travel records remain available offline.")
                TextButton(onClick = { uriHandler.openUri("https://policies.google.com/privacy") }) { Text("Google privacy") }
                TextButton(onClick = { uriHandler.openUri("https://maps.google.com/help/terms_maps/") }) { Text("Maps terms") }
            }
        }, confirmButton = { TextButton(onClick = dismiss) { Text("Done") } },
    )
}

@Composable
internal fun PlaceAttributions(attributions: List<String>) {
    if (attributions.isNotEmpty()) Box(Modifier.fillMaxWidth().heightIn(max = 64.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        AndroidView(
            factory = { context -> TextView(context).apply { movementMethod = LinkMovementMethod.getInstance() } },
            update = { it.text = HtmlCompat.fromHtml(attributions.joinToString("<br>"), HtmlCompat.FROM_HTML_MODE_LEGACY) },
        )
    }
}
