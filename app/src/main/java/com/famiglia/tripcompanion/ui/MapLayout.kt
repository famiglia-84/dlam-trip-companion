package com.famiglia.tripcompanion.ui

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
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
    modifier: Modifier = Modifier, mapSlow: Boolean = false, appearance: (@Composable () -> Unit)? = null,
    bottomOverlay: Dp = 0.dp, unsave: (Place) -> Unit = {},
    map: @Composable (Modifier, PaddingValues) -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    val query by model.query.collectAsStateWithLifecycle()
    var feature by rememberSaveable(state.selected?.id) { mutableStateOf<PlaceFeature?>(null) }
    LaunchedEffect(feature, state.selected?.id) { if (feature == PlaceFeature.Reviews) model.loadReviews() }
    val openedFeature = feature
    DisposableEffect(model, openedFeature, state.selected?.id) {
        onDispose { if (openedFeature == PlaceFeature.Reviews) model.cancelReviewsLoad() }
    }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var expandedMap by rememberSaveable(scopeId) { mutableStateOf(false) }
    var showInfo by rememberSaveable(scopeId) { mutableStateOf(false) }
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

    val sheet = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded, skipHiddenState = false)
    val sheetScaffold = rememberBottomSheetScaffoldState(bottomSheetState = sheet)
    val scope = rememberCoroutineScope()
    val sheetExpanded = sheet.currentValue == SheetValue.Expanded || sheet.targetValue == SheetValue.Expanded
    LaunchedEffect(state.selected?.id) {
        if (state.selected == null || sheet.currentValue == SheetValue.Hidden) {
            if (sheet.currentValue != SheetValue.PartiallyExpanded || sheet.targetValue != SheetValue.PartiallyExpanded)
                sheet.partialExpand()
        }
    }
    // Hidden is a real third anchor: dismiss the selection after a downward gesture
    // settles there. It never removes the user's saved place.
    LaunchedEffect(sheet.currentValue) {
        if (sheet.currentValue == SheetValue.Hidden) model.dismissSelection()
    }
    BackHandler(enabled = state.selected != null) {
        when {
            sheetExpanded -> scope.launch { sheet.partialExpand() }
            expandedMap -> expandedMap = false
            else -> model.dismissSelection()
        }
    }
    LaunchedEffect(state.selected?.id, sheetExpanded) { if (sheetExpanded) model.loadDetails() }

    BoxWithConstraints(modifier.fillMaxSize().imePadding().testTag("map-layout")) {
        val rootHeight = maxHeight
        val footer = minOf(bottomOverlay + 12.dp, rootHeight)
        val sheetHeight = (rootHeight - footer).coerceAtLeast(1.dp)
        val shortWindow = sheetHeight < 420.dp
        val density = LocalDensity.current
        var compactHeight by remember(state.selected?.id, maxWidth, density.fontScale) { mutableIntStateOf(0) }
        val desiredPeek = if (state.selected == null) 0.dp else minOf(sheetHeight,
            if (compactHeight > 0) with(density) { compactHeight.toDp() }
            else if (density.fontScale > 1.3f) 320.dp else 256.dp)
        var controlsHeight by remember { mutableIntStateOf(0) }
        val controlsTop = with(density) { controlsHeight.toDp() }
        // Leave a usable map area above the summary even in split screen with larger text.
        val peek = minOf(desiredPeek, (sheetHeight - (if (expandedMap) 64.dp else controlsTop + 8.dp) - 80.dp).coerceAtLeast(96.dp))
        val offset = runCatching { sheet.requireOffset() }.getOrNull()?.takeIf { it.isFinite() }
        val detailsExpanded = state.selected != null && sheet.currentValue == SheetValue.Expanded &&
            sheet.targetValue == SheetValue.Expanded && offset != null && offset <= 1f
        val covered = if (state.selected == null) 0.dp else with(density) {
            (sheetHeight.toPx() - (offset ?: (sheetHeight - peek).toPx())).coerceIn(0f, sheetHeight.toPx()).toDp()
        }
        val topInset = (if (expandedMap) maxOf(controlsTop, 64.dp) else controlsTop + 8.dp)
            .coerceAtMost((rootHeight - 48.dp).coerceAtLeast(8.dp))
        val bottomInset = (footer + covered + 8.dp)
            .coerceAtMost((rootHeight - topInset - 48.dp).coerceAtLeast(8.dp))
        // The map must be INSIDE the scaffold surface. A transparent full-screen surface
        // placed above a sibling Android View still intercepts its touch hit testing.
        Box(Modifier.fillMaxSize().testTag("map-sheet-viewport").padding(horizontal = 12.dp)
            .then(if (feature != null) Modifier.clearAndSetSemantics { } else Modifier)) {
            BottomSheetScaffold(
                scaffoldState = sheetScaffold,
                sheetPeekHeight = if (state.selected == null) 0.dp else peek + footer, sheetDragHandle = null,
                sheetSwipeEnabled = state.selected != null && feature == null, sheetShadowElevation = 0.dp,
                containerColor = Color.Transparent, sheetContainerColor = Color.Transparent,
                sheetContent = {
                    // The full-height wrapper keeps anchors fixed. Only its visible card
                    // viewport grows during a drag. The surface extends behind the dock,
                    // while a separate content clearance keeps information above it.
                    Column(Modifier.height(rootHeight)) {
                        Box(Modifier.fillMaxWidth().height(if (state.selected == null) 0.dp else covered + footer).clipToBounds()) {
                            key(state.selected?.id) { state.selected?.let { selected ->
                                MapPlaceSheet(selected, places.firstOrNull { it.googlePlaceId == selected.id }, model, state,
                                    covered + footer, shortWindow, sheetExpanded, bottomClearance = footer,
                                    toggle = { scope.launch { if (sheetExpanded) sheet.partialExpand() else sheet.expand() } },
                                    save = { dismissKeyboard(); save(selected.id, selected.name.ifBlank { "Saved place" }, selected.address) },
                                    unsave = unsave,
                                    appearance = appearance, compactHeight = { compactHeight = it }, openFeature = { feature = it })
                            } ?: Spacer(Modifier.height(1.dp)) }
                        }
                    }
                },
            ) {
                // Keep controls laid out during the gesture, excluding them from accessibility
                // only after the expanded card covers them. Sheet anchors remain unchanged.
                Box(Modifier.fillMaxSize().then(if (detailsExpanded) Modifier.clearAndSetSemantics { } else Modifier)) {
                    // Render once, behind the floating toolbar, sheet and navigation dock. SDK insets protect
                    // the attribution, zoom controls and camera focus from all of these overlays.
                    Box(Modifier.fillMaxSize().testTag("map-viewport")) {
                        Surface(Modifier.fillMaxSize().testTag("map-frame"),
                            shape = RoundedCornerShape(28.dp), border = if (detailsExpanded) null else BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))) {
                            map(Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp))
                                .then(if (detailsExpanded) Modifier.clearAndSetSemantics { } else Modifier),
                                if (detailsExpanded) PaddingValues(8.dp) else PaddingValues(start = 8.dp, top = topInset, end = 8.dp, bottom = bottomInset))
                        }
                    }
                    Column(Modifier.fillMaxWidth().onSizeChanged { controlsHeight = it.height }
                        .graphicsLayer { alpha = if (detailsExpanded) 0f else 1f }) {
                        if (!expandedMap) MapControls(query, model::changeQuery, !state.busy, shortWindow,
                            ::search, ::savedPlaces, { dismissKeyboard(); showInfo = true }, ::expand, appearance)
                        val message = state.message ?: if (mapSlow) "The map has not loaded. Check your connection and Maps configuration, then reopen this screen." else null
                        if (message != null) GlassSurface(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(message, Modifier.padding(12.dp).testTag("map-message"), style = MaterialTheme.typography.bodySmall)
                        }
                        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        val credits = (state.pins + listOfNotNull(state.selected)).flatMap(MapLocation::attributions).distinct()
                        if (credits.isNotEmpty()) Surface(shape = RoundedCornerShape(12.dp)) {
                            PlaceAttributions(credits)
                        }
                    }
                    if (expandedMap) GlassSurface(Modifier.align(Alignment.TopEnd).padding(horizontal = 12.dp, vertical = 24.dp)
                        .graphicsLayer { alpha = if (detailsExpanded) 0f else 1f }) {
                        Row {
                            IconButton(onClick = { expandedMap = false }) { Icon(Icons.Default.Search, "Show search controls") }
                            IconButton(onClick = { expandedMap = false }) { Icon(Icons.Default.FullscreenExit, "Exit expanded map") }
                            appearance?.invoke()
                        }
                    }
                    if (state.suggestions.isNotEmpty()) GlassSurface(
                        Modifier.align(Alignment.TopCenter).fillMaxWidth()
                            .padding(top = controlsTop + 4.dp).testTag("map-results"),
                        kind = GlassKind.Details, shape = RoundedCornerShape(20.dp),
                    ) {
                        Column {
                            LazyColumn(Modifier.heightIn(max = minOf(220.dp, sheetHeight * 0.5f))) {
                                items(state.suggestions, key = { it.id }) { suggestion ->
                                    TextButton(onClick = { dismissKeyboard(); model.select(suggestion.id, fromSearch = true) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                                        Column(Modifier.fillMaxWidth()) {
                                            Text(suggestion.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Text(suggestion.subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                            GoogleMapsAttribution(Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                        }
                    }
                }
            }
        }
        val selected = state.selected
        val activeFeature = feature
        if (selected != null && activeFeature != null) {
            PlaceFeatureCard(activeFeature, selected, rootHeight, footer, dismiss = { feature = null }) {
                if (activeFeature == PlaceFeature.Reviews) PlaceReviewsCard(selected, state, model)
                else PlaceStreetView(selected)
            }
        }
    }
    if (showInfo) MapInformation { showInfo = false }
}

@Composable
private fun MapControls(query: String, changeQuery: (String) -> Unit, enabled: Boolean, shortWindow: Boolean,
    search: () -> Unit, savedPlaces: () -> Unit, information: () -> Unit, expand: () -> Unit,
    appearance: (@Composable () -> Unit)?) {
    var showOptions by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().testTag("map-controls").padding(horizontal = 8.dp, vertical = 8.dp)) {
        GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth().testTag("map-toolbar")) {
                val separateActions = !shortWindow && maxWidth >= 344.dp && LocalDensity.current.fontScale <= 1.3f
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        TextButton(onClick = savedPlaces, enabled = enabled,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("map-saved-places")) {
                            Icon(Icons.Outlined.BookmarkBorder, null, Modifier.size(26.dp)); Spacer(Modifier.width(8.dp))
                            Text("Saved places", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    if (separateActions) {
                        IconButton(onClick = information) { Icon(Icons.Outlined.Info, "Map information", Modifier.size(26.dp)) }
                        IconButton(onClick = expand) { Icon(Icons.Default.Fullscreen, "Expand map", Modifier.size(26.dp)) }
                    } else Box {
                        IconButton(onClick = { showOptions = true }) { Icon(Icons.Default.MoreVert, "Map options", Modifier.size(26.dp)) }
                        DropdownMenu(expanded = showOptions, onDismissRequest = { showOptions = false }) {
                            DropdownMenuItem(text = { Text("Map information") }, onClick = { showOptions = false; information() })
                            DropdownMenuItem(text = { Text("Expand map") }, onClick = { showOptions = false; expand() })
                        }
                    }
                    appearance?.invoke()
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        MapSearchField(query, changeQuery, enabled, search, Modifier.fillMaxWidth())
    }
}

/** A 56 dp search field that grows for larger text instead of clipping it. */
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
        modifier = modifier.heightIn(min = 56.dp).testTag(if (search != null) "map-search" else "places-search").semantics { contentDescription = hint },
        decorationBox = { input ->
            GlassSurface(shape = RoundedCornerShape(24.dp), focused = focused) {
                Row(Modifier.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, Modifier.padding(start = 16.dp, end = 12.dp).size(26.dp))
                    Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        if (value.isEmpty()) Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        input()
                    }
                    if (search != null) IconButton(onClick = search, enabled = canSearch) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Search places", Modifier.size(26.dp)) }
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
                Text("Drag the place card up, or use its expand button, for photos, ratings, hours and actions. Tap the rating row to read a selection of Google reviews. Street View opens an interactive panorama when imagery is available; drag only its top handle to move the card. Directions opens Google Maps. Tap a saved place's thumbnail to find it on this map.")
                Text("Google photos, expanded details, reviews and Street View load online and may incur Google Maps Platform charges. Reviews and panoramas load only when you open them. You can choose your own photo in the save dialog; it stays with your selected photo provider and is not uploaded to Google Maps.")
                Text("Maps and Google place details need a connection. Your saved travel records remain available offline.")
                TextButton(onClick = { uriHandler.openUri("https://policies.google.com/privacy") }) { Text("Google privacy") }
                TextButton(onClick = { uriHandler.openUri("https://maps.google.com/help/terms_maps/") }) { Text("Maps terms") }
            }
        }, confirmButton = { TextButton(onClick = dismiss) { Text("Done") } },
    )
}

@Composable
internal fun PlaceAttributions(attributions: List<String>) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    if (attributions.isNotEmpty()) Box(Modifier.fillMaxWidth().heightIn(max = 64.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        AndroidView(
            factory = { context -> TextView(context).apply { movementMethod = LinkMovementMethod.getInstance() } },
            update = {
                it.text = HtmlCompat.fromHtml(attributions.joinToString("<br>"), HtmlCompat.FROM_HTML_MODE_LEGACY)
                it.setTextColor(textColor); it.setLinkTextColor(linkColor)
            },
        )
    }
}
