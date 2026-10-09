package com.famiglia.tripcompanion.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.combine
import com.famiglia.tripcompanion.planning.DayDraft
import com.famiglia.tripcompanion.planning.DaySuggestions

data class TravelData(
    val trips: List<Trip> = emptyList(),
    val reservations: List<Reservation> = emptyList(),
    val places: List<Place> = emptyList(),
    val plans: List<DayPlan> = emptyList(),
    val activities: List<PlanActivity> = emptyList(),
)

/** The local database is the source of truth; future import/planning providers write through here. */
class TripRepository(private val database: TripDatabase) {
    private val dao = database.dao()
    val data = combine(dao.observeTrips(), dao.observeReservations(), dao.observePlaces(), dao.observePlans(), dao.observeActivities()) {
        trips, bookings, places, plans, activities -> TravelData(trips, bookings, places, plans, activities)
    }

    suspend fun save(trip: Trip): Long = database.withTransaction {
        Validation.trip(trip)
        if (trip.id != 0L) {
            require(dao.trip(trip.id) != null) { "This trip was deleted." }
            require(dao.plans(trip.id).all { it.date in trip.startDate..trip.endDate }) {
                "Move or delete day plans outside these dates before changing the trip dates."
            }
        }
        dao.save(trip.copy(destination = trip.destination.trim(), notes = trip.notes.trim()))
    }

    suspend fun save(reservation: Reservation): Long {
        Validation.reservation(reservation)
        require(dao.trip(reservation.tripId) != null) { "Choose an existing trip." }
        return dao.save(reservation.copy(title = reservation.title.trim()))
    }

    suspend fun save(place: Place): Long {
        Validation.place(place)
        place.tripId?.let { require(dao.trip(it) != null) { "Choose an existing trip." } }
        return dao.save(place.copy(name = place.name.trim()))
    }

    suspend fun save(plan: DayPlan): Long = database.withTransaction {
        Validation.date(plan.date)
        val trip = requireNotNull(dao.trip(plan.tripId)) { "Choose an existing trip." }
        require(plan.date in trip.startDate..trip.endDate) { "Choose a date within the trip." }
        require(dao.planOn(plan.tripId, plan.date)?.id.let { it == null || it == plan.id }) { "A plan already exists for this date." }
        dao.save(plan)
    }

    suspend fun save(activity: PlanActivity): Long = database.withTransaction {
        Validation.activity(activity)
        require(dao.plan(activity.planId) != null) { "Choose an existing day plan." }
        activity.placeId?.let { require(dao.place(it) != null) { "This saved place no longer exists." } }
        if (activity.id == 0L) {
            val position = (dao.activities(activity.planId).maxOfOrNull { it.position } ?: -1) + 1
            dao.insert(activity.copy(title = activity.title.trim(), position = position))
        } else {
            require(dao.activities(activity.planId).any { it.id == activity.id }) { "This activity was deleted." }
            dao.update(activity.copy(title = activity.title.trim()))
            activity.id
        }
    }

    suspend fun move(activity: PlanActivity, delta: Int) = database.withTransaction {
        val items = dao.activities(activity.planId).toMutableList()
        val from = items.indexOfFirst { it.id == activity.id }
        val to = from + delta
        if (from >= 0 && to in items.indices) {
            items.add(to, items.removeAt(from))
            items.forEachIndexed { index, item -> dao.update(item.copy(position = index)) }
        }
    }

    /** Revalidate snapshots and regenerate times inside the same transaction as all inserts. */
    suspend fun saveSuggestion(draft: DayDraft): Long = database.withTransaction {
        val request = draft.request
        require(dao.trip(request.trip.id) == request.trip) { "The trip changed or was deleted. Regenerate the draft." }
        request.places.forEach { require(dao.place(it.id) == it) { "A selected place changed or was deleted. Regenerate the draft." } }
        request.reservations.forEach { require(dao.reservation(it.id) == it) { "A selected booking changed or was deleted. Regenerate the draft." } }
        val checked = DaySuggestions.build(request, draft.order, draft.usedAi, draft.elapsedMs)
        require(checked.stops == draft.stops) { "The draft schedule changed. Regenerate it." }
        val existing = dao.planOn(request.trip.id, request.date)
        require(existing == null || dao.activities(existing.id).isEmpty()) { "This day already has activities. Choose a new or empty day; existing plans are kept." }
        val planId = existing?.id ?: dao.save(DayPlan(tripId = request.trip.id, date = request.date,
            title = "A day to explore", notes = "Reviewed ${if (draft.usedAi) "AI-assisted" else "manual"} draft. Transfer buffer: ${request.transferMinutes} min; verify travel times and opening hours."))
        checked.stops.forEachIndexed { index, stop ->
            dao.insert(PlanActivity(planId = planId, title = stop.title, time = DaySuggestions.time(stop.start), position = index,
                placeId = if (stop.isReservation) null else stop.sourceId,
                notes = "Until ${DaySuggestions.time(stop.end)} · ${if (stop.isReservation) "Fixed booking" else "Planned visit"}"))
        }
        planId
    }

    suspend fun delete(trip: Trip) = dao.delete(trip)
    suspend fun delete(reservation: Reservation) = dao.delete(reservation)
    suspend fun delete(place: Place) = dao.delete(place)
    suspend fun delete(plan: DayPlan) = dao.delete(plan)
    suspend fun delete(activity: PlanActivity) = dao.delete(activity)
}
