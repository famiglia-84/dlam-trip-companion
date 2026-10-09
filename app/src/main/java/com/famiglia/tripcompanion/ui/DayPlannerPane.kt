package com.famiglia.tripcompanion.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.famiglia.tripcompanion.data.*
import com.famiglia.tripcompanion.planning.*

@Composable
fun DayPlannerPane(trip: Trip, data: TravelData, model: DayPlannerViewModel, onDismiss: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(trip.id) { model.open(trip) }
    LaunchedEffect(state.savedPlan) { if (state.savedPlan != null) { model.close(); onDismiss() } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val activity = LocalActivity.current
    DisposableEffect(lifecycle, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && activity?.isChangingConfigurations != true) model.stop()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    fun dismiss() { if (!state.saving) { model.close(); onDismiss() } }
    Dialog(onDismissRequest = ::dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 680.dp).fillMaxWidth().fillMaxHeight(0.94f).padding(12.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Suggest a day plan", style = MaterialTheme.typography.headlineSmall)
                Text("Choose saved places and the bookings to keep fixed. Suggestions run on this phone; nothing is saved until you review and save the draft.")
                val form = state.form
                @Composable fun field(label: String, value: String, update: (String) -> Unit) {
                    OutlinedTextField(value, update, enabled = !state.busy, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                field("Plan date (YYYY-MM-DD)", form.date) { value -> model.edit { it.copy(date = value, bookings = emptyList()) } }
                Text("Trip dates: ${trip.startDate} – ${trip.endDate}", style = MaterialTheme.typography.bodySmall)
                field("Start time (HH:mm)", form.start) { value -> model.edit { it.copy(start = value) } }
                field("Finish time (HH:mm)", form.end) { value -> model.edit { it.copy(end = value) } }
                field("Visit duration (minutes)", form.visit) { value -> model.edit { it.copy(visit = value) } }
                field("Transfer buffer (minutes)", form.transfer) { value -> model.edit { it.copy(transfer = value) } }
                Text("Transfer buffers are your estimates, not calculated routes. Verify travel times and opening hours. Bookings without an end time use the visit duration.", style = MaterialTheme.typography.bodySmall)
                Text("Saved places · up to 8", style = MaterialTheme.typography.titleMedium)
                val places = data.places.filter { it.tripId == null || it.tripId == trip.id }
                if (places.isEmpty()) Text("Save places from the Map tab or add them manually first.")
                places.forEach { place ->
                    SelectStop(place.name, "${PlaceCategory.valueOf(place.category).label} · ${place.address}", place.id in form.places, !state.busy, "ai-place-${place.id}") {
                        model.edit { it.copy(places = if (place.id in it.places) it.places - place.id else it.places + place.id) }
                    }
                }
                Text("Fixed bookings · up to 8", style = MaterialTheme.typography.titleMedium)
                Text("Only selected bookings are checked for conflicts. Select every booking this day must protect.", style = MaterialTheme.typography.bodySmall)
                val bookings = data.reservations.filter { it.tripId == trip.id && it.date == form.date }
                if (bookings.isEmpty()) Text("No bookings start on this date.")
                bookings.forEach { booking ->
                    val eligible = booking.time.isNotBlank() && (booking.endDate.isBlank() || booking.endDate == form.date) && booking.kind != ReservationKind.HOTEL.name
                    SelectStop(booking.title, if (eligible) "Fixed at ${booking.time}${booking.endTime.takeIf { it.isNotBlank() }?.let { " – $it" }.orEmpty()}"
                        else "Untimed, overnight or hotel booking: plan manually.", booking.id in form.bookings, !state.busy && eligible, "ai-booking-${booking.id}") {
                        model.edit { it.copy(bookings = if (booking.id in it.bookings) it.bookings - booking.id else it.bookings + booking.id) }
                    }
                }
                HorizontalDivider()
                Text("On-device AI", style = MaterialTheme.typography.titleMedium)
                Text(when (state.availability) {
                    AiAvailability.UNKNOWN -> "Check whether AI is ready in Trip Companion."
                    AiAvailability.AVAILABLE -> "AI is ready on this phone."
                    AiAvailability.DOWNLOADABLE -> "AI is supported; download its model to use it."
                    AiAvailability.DOWNLOADING -> "Android is downloading the model. Wait, then check again."
                    AiAvailability.UNAVAILABLE -> "AI is currently unavailable. You can build without AI or plan manually."
                })
                OutlinedButton(onClick = model::checkAi, enabled = !state.busy, modifier = Modifier.testTag("ai-check")) { Text("Check AI readiness") }
                if (state.availability == AiAvailability.DOWNLOADABLE) {
                    Text("The model download uses internet and device storage through Android. It starts only when you tap Download model.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = model::download, enabled = !state.busy) { Text("Download model") }
                }
                Button(onClick = { model.generate(trip, data, true) }, enabled = !state.busy && state.availability == AiAvailability.AVAILABLE,
                    modifier = Modifier.fillMaxWidth().testTag("ai-generate")) { Text("Generate AI draft") }
                OutlinedButton(onClick = { model.generate(trip, data, false) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Build without AI") }
                if (state.busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(state.progress.orEmpty())
                    if (!state.saving) TextButton(onClick = model::stop) { Text("Stop waiting") }
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("ai-error")) }
                state.draft?.let { draft ->
                    HorizontalDivider()
                    Text("Review your draft", style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("ai-review"))
                    Text("${draft.request.date} · ${if (draft.usedAi) "AI-assisted order (${draft.elapsedMs} ms)" else "Your selected place order"}")
                    Text("Selected bookings stay fixed. Visit and transfer durations are estimates; check them before saving.")
                    draft.stops.forEachIndexed { index, stop ->
                        if (index > 0) {
                            val gap = stop.start - draft.stops[index - 1].end
                            Text("${gap} min between stops (includes transfer buffer)", style = MaterialTheme.typography.bodySmall)
                        }
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text("${DaySuggestions.time(stop.start)} – ${DaySuggestions.time(stop.end)}", color = MaterialTheme.colorScheme.primary)
                                Text(stop.title, style = MaterialTheme.typography.titleMedium)
                                if (stop.isReservation) Text("Fixed booking", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    Text("Change the selections or durations above to rebuild. Existing populated day plans are kept.", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = model::saveDraft, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("ai-save")) { Text("Save reviewed day") }
                }
                TextButton(onClick = ::dismiss, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) { Text("Close") }
            }
        }
    }
}

@Composable
private fun SelectStop(title: String, subtitle: String, checked: Boolean, enabled: Boolean, tag: String, toggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().testTag(tag).toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = { toggle() }).padding(vertical = 4.dp)) {
        Checkbox(checked, null, enabled = enabled)
        Column(Modifier.weight(1f).padding(top = 8.dp)) { Text(title); Text(subtitle, style = MaterialTheme.typography.bodySmall) }
    }
}
