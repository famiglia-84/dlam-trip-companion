package com.famiglia.tripcompanion

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.famiglia.tripcompanion.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RepositoryTest {
    private lateinit var db: TripDatabase
    private lateinit var repository: TripRepository
    private val trip = Trip(destination = "Bari", startDate = "2026-10-10", endDate = "2026-10-14")
    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TripDatabase::class.java).allowMainThreadQueries().build()
        repository = TripRepository(db)
    }
    @After fun tearDown() { db.close() }

    @Test fun createsEditsAndDeletesTrips() = runBlocking {
        val id = repository.save(trip)
        assertTrue(id > 0)
        val stored = repository.data.first().trips.single()
        assertEquals(trip.copy(id = id), stored)
        repository.save(stored.copy(destination = "Rome", notes = "Walk everywhere"))
        assertEquals("Rome", repository.data.first().trips.single().destination)
        repository.delete(stored)
        assertTrue(repository.data.first().trips.isEmpty())
    }

    @Test fun reservationsRoundTripAndCanBeEditedAndRemoved() = runBlocking {
        val tripId = repository.save(trip)
        val id = repository.save(Reservation(tripId = tripId, title = "Hotel", kind = ReservationKind.HOTEL.name,
            date = trip.startDate, time = "15:00", endDate = trip.endDate, endTime = "10:00", confirmation = "ABC123", address = "Old town", notes = "Late arrival"))
        val stored = repository.data.first().reservations.single()
        assertEquals(id, stored.id)
        assertEquals("ABC123", stored.confirmation)
        assertEquals("Late arrival", stored.notes)
        repository.save(stored.copy(confirmation = "UPDATED"))
        assertEquals("UPDATED", repository.data.first().reservations.single().confirmation)
        repository.delete(stored)
        assertTrue(repository.data.first().reservations.isEmpty())
    }

    @Test fun dayPlansValidateDatesAndProtectTripDateEdits() = runBlocking {
        val tripId = repository.save(trip)
        val planId = repository.save(DayPlan(tripId = tripId, date = trip.startDate, notes = "Easy day"))
        repository.save(DayPlan(id = planId, tripId = tripId, date = "2026-10-11", title = "Old town"))
        assertEquals("Old town", repository.data.first().plans.single().title)
        try { repository.save(DayPlan(tripId = tripId, date = "2026-10-09")); fail("Out-of-trip day accepted") } catch (_: IllegalArgumentException) { }
        try { repository.save(DayPlan(tripId = tripId, date = "2026-10-11")); fail("Duplicate day accepted") } catch (_: IllegalArgumentException) { }
        try { repository.save(trip.copy(id = tripId, startDate = "2026-10-12")); fail("Existing plan stranded") } catch (_: IllegalArgumentException) { }
        assertEquals(trip.startDate, repository.data.first().trips.single().startDate)
    }

    @Test fun activitiesCanBeAddedEditedReorderedAndRemoved() = runBlocking {
        val tripId = repository.save(trip)
        val planId = repository.save(DayPlan(tripId = tripId, date = trip.startDate))
        val first = repository.save(PlanActivity(planId = planId, title = "Breakfast", time = "09:00"))
        repository.save(PlanActivity(planId = planId, title = "Castle", time = "10:00"))
        val breakfast = repository.data.first().activities.first()
        repository.move(breakfast, 1)
        assertEquals(listOf("Castle", "Breakfast"), repository.data.first().activities.map { it.title })
        val reordered = repository.data.first().activities.first { it.id == first }
        repository.save(reordered.copy(notes = "Try local bread"))
        assertEquals("Try local bread", repository.data.first().activities.first { it.id == first }.notes)
        repository.delete(reordered)
        assertEquals("Castle", repository.data.first().activities.single().title)
    }

    @Test fun deletingTripCascadesBookingsAndPlansButKeepsPlaces() = runBlocking {
        val tripId = repository.save(trip)
        repository.save(Reservation(tripId = tripId, title = "Flight", date = trip.startDate))
        val placeId = repository.save(Place(tripId = tripId, name = "Castle", address = "Bari"))
        val planId = repository.save(DayPlan(tripId = tripId, date = trip.startDate))
        repository.save(PlanActivity(planId = planId, title = "Castle", placeId = placeId))
        repository.delete(trip.copy(id = tripId))
        val state = repository.data.first()
        assertTrue(state.trips.isEmpty()); assertTrue(state.reservations.isEmpty())
        assertTrue(state.plans.isEmpty()); assertTrue(state.activities.isEmpty())
        assertEquals("Castle", state.places.single().name)
        assertNull(state.places.single().tripId)
    }

    @Test fun deletingSavedPlaceKeepsItineraryActivityAndItsNotes() = runBlocking {
        val tripId = repository.save(trip)
        val placeId = repository.save(Place(name = "Café", notes = "Great coffee"))
        val planId = repository.save(DayPlan(tripId = tripId, date = trip.startDate))
        repository.save(PlanActivity(planId = planId, title = "Café", notes = "Great coffee", placeId = placeId))
        repository.delete(repository.data.first().places.single())
        val activity = repository.data.first().activities.single()
        assertNull(activity.placeId)
        assertEquals("Great coffee", activity.notes)
    }

    @Test fun savedPlacesCanBeEditedAndAssignedToATrip() = runBlocking {
        val tripId = repository.save(trip)
        repository.save(Place(name = "Coffee", category = PlaceCategory.CAFE.name))
        val place = repository.data.first().places.single()
        repository.save(place.copy(tripId = tripId, name = "Morning coffee", address = "Bari old town", notes = "Opens early"))
        val updated = repository.data.first().places.single()
        assertEquals(place.id, updated.id)
        assertEquals(tripId, updated.tripId)
        assertEquals("Morning coffee", updated.name)
        assertEquals("Opens early", updated.notes)
    }

    @Test fun deletingDayRemovesOnlyItsActivities() = runBlocking {
        val tripId = repository.save(trip)
        val first = repository.save(DayPlan(tripId = tripId, date = trip.startDate))
        val second = repository.save(DayPlan(tripId = tripId, date = "2026-10-11"))
        repository.save(PlanActivity(planId = first, title = "Breakfast"))
        repository.save(PlanActivity(planId = second, title = "Castle"))
        repository.delete(repository.data.first().plans.first { it.id == first })
        assertEquals(second, repository.data.first().plans.single().id)
        assertEquals("Castle", repository.data.first().activities.single().title)
        assertEquals(tripId, repository.data.first().trips.single().id)
    }

    @Test fun dataPersistsAcrossDatabaseCloseAndReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "persistence-${UUID.randomUUID()}.db"
        var disk = Room.databaseBuilder(context, TripDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            val repo = TripRepository(disk)
            val tripId = repo.save(trip)
            repo.save(Reservation(tripId = tripId, title = "Flight", date = trip.startDate))
            val placeId = repo.save(Place(tripId = tripId, name = "Old town", notes = "Offline notes"))
            val planId = repo.save(DayPlan(tripId = tripId, date = trip.startDate, title = "Explore"))
            repo.save(PlanActivity(planId = planId, title = "Walk", placeId = placeId))
            disk.close()
            disk = Room.databaseBuilder(context, TripDatabase::class.java, name).allowMainThreadQueries().build()
            val restored = TripRepository(disk).data.first()
            assertEquals(tripId, restored.trips.single().id)
            assertEquals("Flight", restored.reservations.single().title)
            assertEquals("Offline notes", restored.places.single().notes)
            assertEquals("Explore", restored.plans.single().title)
            assertEquals(placeId, restored.activities.single().placeId)
        } finally { disk.close(); context.deleteDatabase(name) }
    }
}
