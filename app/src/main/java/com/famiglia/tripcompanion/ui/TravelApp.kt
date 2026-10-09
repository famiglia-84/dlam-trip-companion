package com.famiglia.tripcompanion.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.famiglia.tripcompanion.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Sections = listOf("Reservations", "Day plans", "Saved places")
private val DateFormat: DateTimeFormatter get() = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
private fun readableDate(date: String): String = runCatching { LocalDate.parse(date).format(DateFormat) }.getOrDefault(date)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelApp(model: TravelViewModel) {
    val data by model.data.collectAsStateWithLifecycle()
    val selected by model.selectedTrip.collectAsStateWithLifecycle()
    val section by model.section.collectAsStateWithLifecycle()
    val theme by model.theme.collectAsStateWithLifecycle()
    val navigator = rememberNavController()
    val entry by navigator.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "trips"
    val snacks = remember { SnackbarHostState() }
    var editor by rememberSaveable { mutableStateOf("") }
    var editId by rememberSaveable { mutableLongStateOf(0L) }
    var parentId by rememberSaveable { mutableLongStateOf(0L) }
    var deletion by rememberSaveable { mutableStateOf("") }
    var deletionId by rememberSaveable { mutableLongStateOf(0L) }
    fun edit(kind: String, id: Long = 0, parent: Long = 0) { editor = kind; editId = id; parentId = parent }
    fun remove(kind: String, id: Long) { deletion = kind; deletionId = id }

    LaunchedEffect(model) { model.messages.collect { snacks.showSnackbar(it) } }
    BackHandler(enabled = route == "trips" && selected != null && editor.isBlank() && deletion.isBlank()) { model.selectTrip(null) }

    TripTheme(theme) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val expanded = maxWidth >= 840.dp
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Column { Text("Trip Companion", fontWeight = FontWeight.SemiBold); Text("A little less planning. A little more adventure.", style = MaterialTheme.typography.labelSmall) } },
                        actions = { IconButton(onClick = model::cycleTheme) { Icon(Icons.Default.Contrast, "Theme: $theme. Change appearance") } },
                    )
                },
                bottomBar = {
                    if (!expanded) NavigationBar {
                        NavigationBarItem(selected = route == "trips", onClick = { navigator.navigate("trips") { launchSingleTop = true; popUpTo("trips"); restoreState = true } },
                            icon = { Icon(Icons.Default.Luggage, null) }, label = { Text("My trips") })
                        NavigationBarItem(selected = route == "places", onClick = { navigator.navigate("places") { launchSingleTop = true } },
                            icon = { Icon(Icons.Default.Bookmarks, null) }, label = { Text("Saved places") })
                    }
                },
                snackbarHost = { SnackbarHost(snacks) },
            ) { padding ->
                Row(Modifier.fillMaxSize().padding(padding)) {
                    if (expanded) NavigationRail {
                        Spacer(Modifier.height(20.dp))
                        NavigationRailItem(selected = route == "trips", onClick = { navigator.navigate("trips") { launchSingleTop = true; popUpTo("trips") } },
                            icon = { Icon(Icons.Default.Luggage, null) }, label = { Text("My trips") })
                        Spacer(Modifier.height(12.dp))
                        NavigationRailItem(selected = route == "places", onClick = { navigator.navigate("places") { launchSingleTop = true } },
                            icon = { Icon(Icons.Default.Bookmarks, null) }, label = { Text("Places") })
                    }
                    NavHost(navigator, startDestination = "trips", modifier = Modifier.weight(1f)) {
                        composable("trips") {
                            val trip = data.trips.firstOrNull { it.id == selected }
                            if (expanded) {
                                Row(Modifier.fillMaxSize().testTag("expanded-layout")) {
                                    TripsPane(data.trips, selected, { model.selectTrip(it); model.selectSection("Reservations") }, { edit("trip") }, Modifier.width(340.dp), true)
                                    VerticalDivider()
                                    if (trip == null) EmptyState("Where will you go next?", "Choose a trip to see your bookings, day plans and favourite places side by side.", Icons.Default.Explore, Modifier.weight(1f))
                                    else TripDetails(trip, data, section, model::selectSection, { model.selectTrip(null) }, ::edit, ::remove, { item, delta -> model.perform { model.move(item, delta) } }, Modifier.weight(1f))
                                }
                            } else {
                                Box(Modifier.fillMaxSize().testTag("compact-layout")) {
                                    if (trip == null) TripsPane(data.trips, selected, { model.selectTrip(it); model.selectSection("Reservations") }, { edit("trip") }, Modifier.fillMaxSize())
                                    else TripDetails(trip, data, section, model::selectSection, { model.selectTrip(null) }, ::edit, ::remove, { item, delta -> model.perform { model.move(item, delta) } }, Modifier.fillMaxSize())
                                }
                            }
                        }
                        composable("places") {
                            PlacesPane(data.places, data.trips, { edit("place") }, { edit("place", it.id) }, { remove("place", it.id) })
                        }
                    }
                }
            }

            when (editor) {
                "trip" -> TripEditor(data.trips.firstOrNull { it.id == editId }, model, { id -> model.selectTrip(id) }, { editor = "" })
                "reservation" -> data.trips.firstOrNull { it.id == parentId }?.let { trip -> ReservationEditor(data.reservations.firstOrNull { it.id == editId }, trip, model) { editor = "" } }
                "place" -> PlaceEditor(data.places.firstOrNull { it.id == editId }, parentId.takeIf { it != 0L }, data.trips, model) { editor = "" }
                "day" -> data.trips.firstOrNull { it.id == parentId }?.let { trip -> DayEditor(data.plans.firstOrNull { it.id == editId }, trip, model) { editor = "" } }
                "activity" -> data.plans.firstOrNull { it.id == parentId }?.let { plan ->
                    ActivityEditor(data.activities.firstOrNull { it.id == editId }, plan, data.places.filter { it.tripId == null || it.tripId == plan.tripId }, model) { editor = "" }
                }
            }
            if (deletion.isNotBlank()) AlertDialog(
                onDismissRequest = { deletion = "" },
                title = { Text("Delete ${if (deletion == "day") "day plan" else deletion}?") },
                text = { Text(when (deletion) {
                    "trip" -> "This removes the trip, its reservations and all its day plans. Saved places stay in your global list. This cannot be undone."
                    "day" -> "This removes the day plan and all its activities. This cannot be undone."
                    "place" -> "This removes the saved place. Existing itinerary activities and their notes are kept. This cannot be undone."
                    else -> "This removes the item from your trip. This cannot be undone."
                }) },
                confirmButton = { TextButton(onClick = {
                    val kind = deletion
                    val id = deletionId
                    model.perform {
                        when (kind) {
                            "trip" -> data.trips.firstOrNull { it.id == id }?.let { model.delete(it); if (selected == id) model.selectTrip(null) }
                            "reservation" -> data.reservations.firstOrNull { it.id == id }?.let { model.delete(it) }
                            "place" -> data.places.firstOrNull { it.id == id }?.let { model.delete(it) }
                            "day" -> data.plans.firstOrNull { it.id == id }?.let { model.delete(it) }
                            "activity" -> data.activities.firstOrNull { it.id == id }?.let { model.delete(it) }
                        }
                    }
                    deletion = ""
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
                dismissButton = { TextButton(onClick = { deletion = "" }) { Text("Keep it") } },
            )
        }
    }
}

/** Width-based layout, with selection and editor drafts held outside layout branches. */
@Composable
fun TripsPane(trips: List<Trip>, selected: Long?, select: (Long) -> Unit, add: () -> Unit, modifier: Modifier = Modifier, singleColumn: Boolean = false) {
    BoxWithConstraints(modifier) {
        val columns = if (!singleColumn && maxWidth >= 600.dp) 2 else 1
        Column(Modifier.fillMaxSize()) {
            PaneHeading("My trips", "Your world, one trip at a time.", "New trip", add)
            if (trips.isEmpty()) {
                EmptyState("Your next chapter starts here", "From a weekend escape to the journey of a lifetime, keep all the details together.", Icons.Default.TravelExplore, Modifier.weight(1f))
            } else {
                // Recomputed whenever the screen enters composition; dates are device-local.
                val today = LocalDate.now()
                LazyVerticalGrid(GridCells.Fixed(columns), modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TripPeriod.entries.forEach { period ->
                        val items = trips.filter { it.period(today) == period }.let { if (period == TripPeriod.PAST) it.sortedByDescending { t -> t.startDate } else it }
                        if (items.isNotEmpty()) {
                            item(key = period.name, span = { GridItemSpan(maxLineSpan) }) { Text(period.label.uppercase(Locale.getDefault()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
                            items(items, key = { it.id }) { trip -> TripCard(trip, trip.id == selected) { select(trip.id) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TripCard(trip: Trip, selected: Boolean, click: () -> Unit) {
    Card(onClick = click, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {
        DestinationPhoto(trip, Modifier.fillMaxWidth().height(170.dp))
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(trip.destination, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("${readableDate(trip.startDate)} – ${readableDate(trip.endDate)}", style = MaterialTheme.typography.bodyMedium)
            Text(trip.countdown(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun DestinationPhoto(trip: Trip, modifier: Modifier) {
    Box(modifier) {
        // Original vector scenery is the fallback; no Google artwork or bundled third-party photos.
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFFEDD3A2), Color(0xFFCCE2D8))))
            drawCircle(Color(0xFFFFF0B6), size.minDimension * .19f, Offset(size.width * .76f, size.height * .26f))
            drawPath(Path().apply { moveTo(0f, size.height * .85f); lineTo(size.width * .29f, size.height * .18f); lineTo(size.width * .62f, size.height * .88f); lineTo(size.width * .86f, size.height * .42f); lineTo(size.width, size.height * .69f); lineTo(size.width, size.height); lineTo(0f, size.height); close() }, Color(0xFF669A88))
            drawPath(Path().apply { moveTo(0f, size.height * .77f); cubicTo(size.width * .45f, size.height * .42f, size.width * .50f, size.height * 1.1f, size.width, size.height * .72f); lineTo(size.width, size.height); lineTo(0f, size.height); close() }, Color(0xFF245F54))
        }
        if (trip.photoUri.isNotBlank()) AsyncImage(model = trip.photoUri, contentDescription = "${trip.destination} destination photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun TripDetails(trip: Trip, data: TravelData, section: String, sectionChange: (String) -> Unit, back: () -> Unit,
    edit: (String, Long, Long) -> Unit, remove: (String, Long) -> Unit, move: (PlanActivity, Int) -> Unit, modifier: Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to my trips") }
            Text(trip.destination, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 2)
            EditDelete("trip", { edit("trip", trip.id, 0) }, { remove("trip", trip.id) })
        }
        ScrollableTabRow(selectedTabIndex = Sections.indexOf(section).coerceAtLeast(0), edgePadding = 12.dp) {
            Sections.forEach { value -> Tab(selected = section == value, onClick = { sectionChange(value) }, text = { Text(value) }) }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "overview") {
                Card(shape = RoundedCornerShape(24.dp)) {
                    DestinationPhoto(trip, Modifier.fillMaxWidth().height(150.dp))
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${readableDate(trip.startDate)} – ${readableDate(trip.endDate)}", style = MaterialTheme.typography.titleMedium)
                        Text(trip.countdown(), color = MaterialTheme.colorScheme.primary)
                        if (trip.notes.isNotBlank()) Text(trip.notes)
                    }
                }
            }
            when (section) {
                "Reservations" -> {
                    val bookings = data.reservations.filter { it.tripId == trip.id }
                    item { SectionHeading("All your bookings", "Add reservation") { edit("reservation", 0, trip.id) } }
                    if (bookings.isEmpty()) item { InlineEmpty("Flights, stays, tables and trains.", "Add a booking to keep its details close at hand.") }
                    items(bookings, key = { "reservation-${it.id}" }) { item ->
                        InfoCard(item.title, "${ReservationKind.valueOf(item.kind).label} · ${readableDate(item.date)} ${item.time}", Icons.Default.ConfirmationNumber,
                            { edit("reservation", item.id, trip.id) }, { remove("reservation", item.id) }) {
                            if (item.endDate.isNotBlank()) Text("Until ${readableDate(item.endDate)} ${item.endTime}")
                            if (item.confirmation.isNotBlank()) Text("Confirmation: ${item.confirmation}", fontWeight = FontWeight.Medium)
                            if (item.address.isNotBlank()) Text(item.address)
                            if (item.notes.isNotBlank()) Text(item.notes)
                        }
                    }
                }
                "Day plans" -> {
                    val days = data.plans.filter { it.tripId == trip.id }
                    item { SectionHeading("Make room for discovery", "Add day plan") { edit("day", 0, trip.id) } }
                    if (days.isEmpty()) item { InlineEmpty("A day at a time.", "Create a plan, then add activities or your saved places. Everything is kept offline.") }
                    items(days, key = { "day-${it.id}" }) { plan ->
                        val activities = data.activities.filter { it.planId == plan.id }
                        Card(shape = RoundedCornerShape(20.dp)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(readableDate(plan.date), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                        if (plan.title.isNotBlank()) Text(plan.title, style = MaterialTheme.typography.titleLarge)
                                    }
                                    EditDelete("day plan", { edit("day", plan.id, trip.id) }, { remove("day", plan.id) })
                                }
                                if (plan.notes.isNotBlank()) Text(plan.notes)
                                activities.forEachIndexed { index, activity ->
                                    HorizontalDivider()
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(activity.time.ifBlank { "Any time" }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(76.dp))
                                            Text(activity.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                        }
                                        if (activity.notes.isNotBlank()) Text(activity.notes, style = MaterialTheme.typography.bodyMedium)
                                        activity.placeId?.let { id -> data.places.firstOrNull { it.id == id }?.let { Text("Saved place: ${it.name}", style = MaterialTheme.typography.labelMedium) } }
                                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                            IconButton(enabled = index > 0, onClick = { move(activity, -1) }) { Icon(Icons.Default.ArrowUpward, "Move ${activity.title} up") }
                                            IconButton(enabled = index < activities.lastIndex, onClick = { move(activity, 1) }) { Icon(Icons.Default.ArrowDownward, "Move ${activity.title} down") }
                                            EditDelete("activity ${activity.title}", { edit("activity", activity.id, plan.id) }, { remove("activity", activity.id) })
                                        }
                                    }
                                }
                                if (activities.isEmpty()) Text("An open day. What would you like to do?", style = MaterialTheme.typography.bodyMedium)
                                OutlinedButton(onClick = { edit("activity", 0, plan.id) }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Add activity") }
                            }
                        }
                    }
                }
                "Saved places" -> {
                    val places = data.places.filter { it.tripId == trip.id }
                    item { SectionHeading("Places worth remembering", "Save a place") { edit("place", 0, trip.id) } }
                    if (places.isEmpty()) item { InlineEmpty("Keep your favourites nearby.", "Save a place for this trip, or choose an unassigned place when adding an activity.") }
                    items(places, key = { "place-${it.id}" }) { place -> PlaceCard(place, null, { edit("place", place.id, trip.id) }, { remove("place", place.id) }) }
                }
            }
        }
    }
}

@Composable
private fun PlacesPane(places: List<Place>, trips: List<Trip>, add: () -> Unit, edit: (Place) -> Unit, remove: (Place) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("All") }
    Column(Modifier.fillMaxSize()) {
        PaneHeading("Saved places", "The spots you don't want to forget.", "Save a place", add)
        OutlinedTextField(query, { query = it }, label = { Text("Search places") }, singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf("All") + PlaceCategory.entries.map { it.label }).forEach { label -> FilterChip(selected = category == label, onClick = { category = label }, label = { Text(label) }) }
        }
        val filtered = places.filter { (category == "All" || PlaceCategory.valueOf(it.category).label == category) && (it.name.contains(query, true) || it.address.contains(query, true) || it.notes.contains(query, true)) }
        if (filtered.isEmpty()) EmptyState(if (places.isEmpty()) "Collect a little inspiration" else "No places found", if (places.isEmpty()) "Save a café, a landmark or somewhere special. Assign it to a trip now or later." else "Try another search or category.", Icons.Default.BookmarkBorder, Modifier.weight(1f))
        else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(filtered, key = { it.id }) { place -> PlaceCard(place, trips.firstOrNull { it.id == place.tripId }?.destination ?: "Unassigned", { edit(place) }, { remove(place) }) }
        }
    }
}

@Composable
private fun PlaceCard(place: Place, tripLabel: String?, edit: () -> Unit, remove: () -> Unit) {
    InfoCard(place.name, PlaceCategory.valueOf(place.category).label + (tripLabel?.let { " · $it" } ?: ""), Icons.Default.Place, edit, remove) {
        if (place.address.isNotBlank()) Text(place.address)
        if (place.notes.isNotBlank()) Text(place.notes)
    }
}

@Composable
private fun InfoCard(title: String, subtitle: String, icon: ImageVector, edit: () -> Unit, remove: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(subtitle, style = MaterialTheme.typography.bodySmall) }
            }
            content()
            Row(Modifier.align(Alignment.End)) { EditDelete(title, edit, remove) }
        }
    }
}

@Composable
private fun EditDelete(label: String, edit: () -> Unit, remove: () -> Unit) {
    IconButton(onClick = edit) { Icon(Icons.Default.Edit, "Edit $label") }
    IconButton(onClick = remove) { Icon(Icons.Default.DeleteOutline, "Delete $label") }
}

@Composable
private fun PaneHeading(title: String, subtitle: String, action: String, click: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalButton(onClick = click) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(action) }
    }
}

@Composable
private fun SectionHeading(title: String, action: String, click: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Button(onClick = click) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(action) } }
}

@Composable
private fun InlineEmpty(title: String, text: String) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(20.dp)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyState(title: String, text: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 440.dp).padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
