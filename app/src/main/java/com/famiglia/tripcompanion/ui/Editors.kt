package com.famiglia.tripcompanion.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.famiglia.tripcompanion.data.*
import com.famiglia.tripcompanion.maps.MapViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
private fun Editor(title: String, onDismiss: () -> Unit, onSave: suspend () -> Unit, fields: @Composable ColumnScope.() -> Unit) {
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                fields()
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = !saving, onClick = {
                saving = true
                scope.launch {
                    try { onSave(); onDismiss() }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = e.message ?: "Could not save. Please try again." }
                    finally { saving = false }
                }
            }) { Text(if (saving) "Saving…" else "Save") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Field(label: String, value: String, change: (String) -> Unit, multiline: Boolean = false) {
    OutlinedTextField(value, change, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = !multiline, minLines = if (multiline) 2 else 1)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, value: String, change: (String) -> Unit, optional: Boolean = false) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    Field("$label (YYYY-MM-DD${if (optional) ", optional" else ""})", value, change)
    Row {
        TextButton(onClick = { choosing = true }) { Text("Choose $label") }
        if (optional && value.isNotBlank()) TextButton(onClick = { change("") }) { Text("Clear") }
    }
    if (choosing) {
        val initial = runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate.now()).toEpochDay() * 86_400_000
        val picker = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(onDismissRequest = { choosing = false }, confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.let { change(LocalDate.ofEpochDay(it / 86_400_000).toString()) }
                choosing = false
            }) { Text("Use date") }
        }, dismissButton = { TextButton(onClick = { choosing = false }) { Text("Cancel") } }) { DatePicker(picker) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Choices(label: String, value: String, choices: List<String>, change: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
        OutlinedTextField(value, {}, readOnly = true, label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable))
        ExposedDropdownMenu(expanded, { expanded = false }) {
            choices.forEach { choice -> DropdownMenuItem(text = { Text(choice) }, onClick = { change(choice); expanded = false }) }
        }
    }
}

@Composable
fun TripEditor(existing: Trip?, model: TravelViewModel, onSaved: (Long) -> Unit, onDismiss: () -> Unit) {
    var destination by rememberSaveable { mutableStateOf(existing?.destination ?: "") }
    var start by rememberSaveable { mutableStateOf(existing?.startDate ?: LocalDate.now().toString()) }
    var end by rememberSaveable { mutableStateOf(existing?.endDate ?: LocalDate.now().plusDays(4).toString()) }
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }
    var photo by rememberSaveable { mutableStateOf(existing?.photoUri ?: "") }
    var photoError by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                photo = uri.toString()
                photoError = null
            } catch (_: SecurityException) { photoError = "This provider cannot keep the photo available. Choose a photo stored on your phone." }
        }
    }
    Editor(if (existing == null) "A new adventure" else "Edit trip", onDismiss, {
        val id = model.save(Trip(existing?.id ?: 0, destination, start, end, notes, photo))
        onSaved(if (existing == null) id else existing.id)
    }) {
        Field("Destination", destination, { destination = it })
        DateField("Start date", start, { start = it })
        DateField("End date", end, { end = it })
        Field("Personal notes", notes, { notes = it }, true)
        Row {
            TextButton(onClick = { photoPicker.launch(arrayOf("image/*")) }) { Text(if (photo.isBlank()) "Add destination photo" else "Change photo") }
            if (photo.isNotBlank()) TextButton(onClick = { photo = "" }) { Text("Remove") }
        }
        Text("Choose your own photo. It stays on your phone and is available offline.", style = MaterialTheme.typography.bodySmall)
        photoError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
fun ReservationEditor(existing: Reservation?, trip: Trip, model: TravelViewModel, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf(existing?.title ?: "") }
    var kind by rememberSaveable { mutableStateOf(existing?.kind ?: ReservationKind.FLIGHT.name) }
    var date by rememberSaveable { mutableStateOf(existing?.date ?: trip.startDate) }
    var time by rememberSaveable { mutableStateOf(existing?.time ?: "") }
    var endDate by rememberSaveable { mutableStateOf(existing?.endDate ?: "") }
    var endTime by rememberSaveable { mutableStateOf(existing?.endTime ?: "") }
    var confirmation by rememberSaveable { mutableStateOf(existing?.confirmation ?: "") }
    var address by rememberSaveable { mutableStateOf(existing?.address ?: "") }
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }
    Editor(if (existing == null) "Add reservation" else "Edit reservation", onDismiss, {
        model.save(Reservation(existing?.id ?: 0, trip.id, title, kind, date, time, endDate, endTime, confirmation, address, notes))
    }) {
        Field("Booking title", title, { title = it })
        Choices("Type", ReservationKind.valueOf(kind).label, ReservationKind.entries.map { it.label }) { chosen -> kind = ReservationKind.entries.first { it.label == chosen }.name }
        DateField("Date", date, { date = it })
        Field("Time (HH:mm, optional)", time, { time = it })
        DateField("End date", endDate, { endDate = it }, optional = true)
        Field("End time (HH:mm, optional)", endTime, { endTime = it })
        Text("Times are local to the booking location; no time-zone conversion is applied.", style = MaterialTheme.typography.bodySmall)
        Field("Confirmation number", confirmation, { confirmation = it })
        Field("Address / route", address, { address = it })
        Field("Notes", notes, { notes = it }, true)
    }
}

@Composable
fun PlaceEditor(existing: Place?, defaultTripId: Long?, trips: List<Trip>, model: TravelViewModel,
    suggestedGoogleId: String? = null, suggestedName: String = "", suggestedAddress: String = "", photos: MapViewModel? = null, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(existing?.name ?: suggestedName) }
    var googleId by rememberSaveable { mutableStateOf(existing?.googlePlaceId ?: suggestedGoogleId) }
    var category by rememberSaveable { mutableStateOf(existing?.category ?: PlaceCategory.ATTRACTION.name) }
    var tripId by rememberSaveable { mutableStateOf(existing?.tripId ?: defaultTripId) }
    var address by rememberSaveable { mutableStateOf(existing?.address ?: suggestedAddress) }
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }
    var photoUri by rememberSaveable { mutableStateOf(existing?.photoUri ?: "") }
    var photoError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                photoUri = uri.toString(); photoError = null
            } catch (_: SecurityException) { photoError = "Choose a photo stored on your device so it remains available." }
        }
    }
    val photo = rememberPlaceThumbnail(googleId, photoUri, photos)
    val options = listOf("All trips / unassigned") + trips.map { "${it.destination} · ${it.startDate} (#${it.id})" }
    Editor(if (existing == null) "Save a place" else "Edit saved place", onDismiss, {
        model.save(Place(existing?.id ?: 0, tripId, name, category, address, notes, googleId, photoUri))
    }) {
        PlaceImage(photoUri, photo, name, Modifier.fillMaxWidth().height(120.dp))
        if (photoUri.isBlank()) PhotoCredits(photo)
        Row {
            TextButton(onClick = { photoPicker.launch(arrayOf("image/*")) }) { Text("Choose your photo") }
            if (photoUri.isNotBlank()) TextButton(onClick = { photoUri = "" }) { Text("Use default image") }
        }
        photoError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Field("Place name", name, { name = it })
        Choices("Category", PlaceCategory.valueOf(category).label, PlaceCategory.entries.map { it.label }) { chosen -> category = PlaceCategory.entries.first { it.label == chosen }.name }
        Choices("Trip", options.getOrElse(trips.indexOfFirst { it.id == tripId } + 1) { options.first() }, options) { chosen ->
            tripId = options.indexOf(chosen).takeIf { it > 0 }?.let { trips[it - 1].id }
        }
        Field("Address", address, { address = it })
        Field("Notes", notes, { notes = it }, true)
        if (googleId != null) {
            Text("Google Maps link attached. Your label, address and notes stay on your device; map details load online.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { googleId = null }) { Text("Remove Google Maps link") }
        }
    }
}

@Composable
fun DayEditor(existing: DayPlan?, trip: Trip, model: TravelViewModel, onDismiss: () -> Unit) {
    var date by rememberSaveable { mutableStateOf(existing?.date ?: trip.startDate) }
    var title by rememberSaveable { mutableStateOf(existing?.title ?: "") }
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }
    Editor(if (existing == null) "Create day plan" else "Edit day plan", onDismiss, {
        model.save(DayPlan(existing?.id ?: 0, trip.id, date, title, notes))
    }) {
        DateField("Plan date", date, { date = it })
        Text("Choose a day between ${trip.startDate} and ${trip.endDate}.", style = MaterialTheme.typography.bodySmall)
        Field("Day title (optional)", title, { title = it })
        Field("Day notes", notes, { notes = it }, true)
    }
}

@Composable
fun ActivityEditor(existing: PlanActivity?, plan: DayPlan, places: List<Place>, model: TravelViewModel, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf(existing?.title ?: "") }
    var time by rememberSaveable { mutableStateOf(existing?.time ?: "") }
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }
    var placeId by rememberSaveable { mutableStateOf(existing?.placeId) }
    val options = listOf("Custom activity") + places.map { "${it.name} (#${it.id})" }
    Editor(if (existing == null) "Add activity" else "Edit activity", onDismiss, {
        model.save(PlanActivity(existing?.id ?: 0, plan.id, title, time, notes, existing?.position ?: 0, placeId))
    }) {
        Choices("Saved place", options.getOrElse(places.indexOfFirst { it.id == placeId } + 1) { options.first() }, options) { chosen ->
            val place = options.indexOf(chosen).takeIf { it > 0 }?.let { places[it - 1] }
            placeId = place?.id
            if (place != null) {
                title = place.name
                notes = listOf(place.address, place.notes).filter { it.isNotBlank() }.joinToString("\n")
            }
        }
        Field("Activity title", title, { title = it })
        Field("Time (HH:mm, optional)", time, { time = it })
        Field("Notes / address", notes, { notes = it }, true)
        Text("Activities follow your chosen order. Use the up/down controls to rearrange them.", style = MaterialTheme.typography.bodySmall)
    }
}
