package com.famiglia.tripcompanion

import com.famiglia.tripcompanion.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ValidationTest {
    private val trip = Trip(destination = "Bari", startDate = "2026-10-10", endDate = "2026-10-14")

    @Test fun tripDatesAreValidatedStrictly() {
        Validation.trip(trip)
        listOf("2026-02-30", "2026-13-01", "26-10-10", "2026-1-10").forEach { bad ->
            assertThrows(IllegalArgumentException::class.java) { Validation.trip(trip.copy(startDate = bad)) }
        }
        assertThrows(IllegalArgumentException::class.java) { Validation.trip(trip.copy(endDate = "2026-10-09")) }
        assertThrows(IllegalArgumentException::class.java) { Validation.trip(trip.copy(destination = "  ")) }
        Validation.trip(trip.copy(endDate = trip.startDate))
    }

    @Test fun tripPeriodsAndCountdownIncludeBothBoundaryDays() {
        assertEquals(TripPeriod.UPCOMING, trip.period(LocalDate.parse("2026-10-09")))
        assertEquals("1 day to go", trip.countdown(LocalDate.parse("2026-10-09")))
        assertEquals(TripPeriod.CURRENT, trip.period(LocalDate.parse("2026-10-10")))
        assertEquals(TripPeriod.CURRENT, trip.period(LocalDate.parse("2026-10-14")))
        assertEquals(TripPeriod.PAST, trip.period(LocalDate.parse("2026-10-15")))
    }

    @Test fun bookingTimesAndEndDatesAreValidated() {
        val booking = Reservation(tripId = 1, title = "Flight", date = "2026-10-10", time = "09:30")
        Validation.reservation(booking)
        assertThrows(IllegalArgumentException::class.java) { Validation.reservation(booking.copy(time = "24:01")) }
        assertThrows(IllegalArgumentException::class.java) { Validation.reservation(booking.copy(endTime = "10:00")) }
        assertThrows(IllegalArgumentException::class.java) { Validation.reservation(booking.copy(endDate = "2026-10-09")) }
        assertThrows(IllegalArgumentException::class.java) { Validation.reservation(booking.copy(endDate = booking.date, endTime = "08:00")) }
        Validation.reservation(booking.copy(endDate = "2026-10-11", endTime = "08:00"))
        Validation.reservation(booking.copy(time = ""))
    }

    @Test fun activitiesAndPlacesNeedNames() {
        assertThrows(IllegalArgumentException::class.java) { Validation.activity(PlanActivity(planId = 1, title = " ")) }
        assertThrows(IllegalArgumentException::class.java) { Validation.activity(PlanActivity(planId = 1, title = "Walk", time = "9am")) }
        assertThrows(IllegalArgumentException::class.java) { Validation.place(Place(name = "")) }
    }
}
