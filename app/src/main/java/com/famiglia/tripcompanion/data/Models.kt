package com.famiglia.tripcompanion.data

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val destination: String,
    val startDate: String,
    val endDate: String,
    val notes: String = "",
    val photoUri: String = "",
)

enum class ReservationKind(val label: String) {
    FLIGHT("Flight"), HOTEL("Hotel"), TRANSPORT("Train / transport"), RESTAURANT("Restaurant"), OTHER("Other")
}

@Entity(
    tableName = "reservations",
    foreignKeys = [ForeignKey(entity = Trip::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
data class Reservation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val title: String,
    val kind: String = ReservationKind.OTHER.name,
    val date: String,
    val time: String = "",
    val endDate: String = "",
    val endTime: String = "",
    val confirmation: String = "",
    val address: String = "",
    val notes: String = "",
)

enum class PlaceCategory(val label: String) {
    ATTRACTION("Attraction"), RESTAURANT("Restaurant"), CAFE("Café"), SHOPPING("Shopping"), LANDMARK("Landmark"), CUSTOM("Custom")
}

@Entity(
    tableName = "places",
    foreignKeys = [ForeignKey(entity = Trip::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("tripId")],
)
data class Place(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long? = null,
    val name: String,
    val category: String = PlaceCategory.ATTRACTION.name,
    val address: String = "",
    val notes: String = "",
    val googlePlaceId: String? = null,
    @ColumnInfo(defaultValue = "''") val photoUri: String = "",
)

@Entity(
    tableName = "day_plans",
    foreignKeys = [ForeignKey(entity = Trip::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId"), Index(value = ["tripId", "date"], unique = true)],
)
data class DayPlan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val date: String,
    val title: String = "",
    val notes: String = "",
)

@Entity(
    tableName = "activities",
    foreignKeys = [
        ForeignKey(entity = DayPlan::class, parentColumns = ["id"], childColumns = ["planId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Place::class, parentColumns = ["id"], childColumns = ["placeId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("planId"), Index("placeId")],
)
data class PlanActivity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val title: String,
    val time: String = "",
    val notes: String = "",
    val position: Int = 0,
    val placeId: Long? = null,
)

enum class TripPeriod(val label: String) { CURRENT("On the road"), UPCOMING("Coming up"), PAST("Memories") }

fun Trip.period(today: LocalDate = LocalDate.now()): TripPeriod = when {
    LocalDate.parse(endDate).isBefore(today) -> TripPeriod.PAST
    LocalDate.parse(startDate).isAfter(today) -> TripPeriod.UPCOMING
    else -> TripPeriod.CURRENT
}

fun Trip.countdown(today: LocalDate = LocalDate.now()): String = when (period(today)) {
    TripPeriod.CURRENT -> "Enjoy your trip"
    TripPeriod.PAST -> "A trip to remember"
    TripPeriod.UPCOMING -> ChronoUnit.DAYS.between(today, LocalDate.parse(startDate)).let { "$it ${if (it == 1L) "day" else "days"} to go" }
}

object Validation {
    fun date(value: String, label: String = "Date"): LocalDate {
        require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(value)) { "$label must use YYYY-MM-DD." }
        return try { LocalDate.parse(value) } catch (_: Exception) { throw IllegalArgumentException("$label is not a valid date.") }
    }
    fun time(value: String) {
        if (value.isBlank()) return
        require(Regex("\\d{2}:\\d{2}").matches(value)) { "Time must use HH:mm (24-hour)." }
        try { LocalTime.parse(value) } catch (_: Exception) { throw IllegalArgumentException("Enter a valid time, such as 09:30.") }
    }
    fun trip(trip: Trip) {
        require(trip.destination.isNotBlank()) { "Enter a destination." }
        require(!date(trip.endDate, "End date").isBefore(date(trip.startDate, "Start date"))) { "End date must be on or after the start date." }
    }
    fun reservation(reservation: Reservation) {
        require(reservation.title.isNotBlank()) { "Enter a booking title." }
        val start = date(reservation.date)
        time(reservation.time)
        time(reservation.endTime)
        require(reservation.endTime.isBlank() || reservation.endDate.isNotBlank()) { "Enter an end date with the end time." }
        if (reservation.endDate.isNotBlank()) {
            val end = date(reservation.endDate, "End date")
            require(!end.isBefore(start)) { "Booking end date must be on or after its start date." }
            if (end == start && reservation.time.isNotBlank() && reservation.endTime.isNotBlank()) {
                require(reservation.endTime >= reservation.time) { "End time must be on or after the start time." }
            }
        }
        require(ReservationKind.entries.any { it.name == reservation.kind }) { "Choose a booking type." }
    }
    fun place(place: Place) {
        require(place.name.isNotBlank()) { "Enter a place name." }
        require(PlaceCategory.entries.any { it.name == place.category }) { "Choose a place category." }
    }
    fun activity(activity: PlanActivity) {
        require(activity.title.isNotBlank()) { "Enter an activity title." }
        time(activity.time)
    }
}
