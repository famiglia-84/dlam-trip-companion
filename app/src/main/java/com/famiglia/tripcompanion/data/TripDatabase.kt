package com.famiglia.tripcompanion.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY startDate, id") fun observeTrips(): Flow<List<Trip>>
    @Query("SELECT * FROM reservations ORDER BY date, time, id") fun observeReservations(): Flow<List<Reservation>>
    @Query("SELECT * FROM places ORDER BY name COLLATE NOCASE, id") fun observePlaces(): Flow<List<Place>>
    @Query("SELECT * FROM day_plans ORDER BY date, id") fun observePlans(): Flow<List<DayPlan>>
    @Query("SELECT * FROM activities ORDER BY position, id") fun observeActivities(): Flow<List<PlanActivity>>
    @Query("SELECT * FROM trips WHERE id = :id") suspend fun trip(id: Long): Trip?
    @Query("SELECT * FROM day_plans WHERE id = :id") suspend fun plan(id: Long): DayPlan?
    @Query("SELECT * FROM day_plans WHERE tripId = :tripId ORDER BY date") suspend fun plans(tripId: Long): List<DayPlan>
    @Query("SELECT * FROM day_plans WHERE tripId = :tripId AND date = :date") suspend fun planOn(tripId: Long, date: String): DayPlan?
    @Query("SELECT * FROM activities WHERE planId = :planId ORDER BY position, id") suspend fun activities(planId: Long): List<PlanActivity>
    @Query("SELECT * FROM places WHERE id = :id") suspend fun place(id: Long): Place?
    @Upsert suspend fun save(trip: Trip): Long
    @Upsert suspend fun save(reservation: Reservation): Long
    @Upsert suspend fun save(place: Place): Long
    @Upsert suspend fun save(plan: DayPlan): Long
    @Insert suspend fun insert(activity: PlanActivity): Long
    @Update suspend fun update(activity: PlanActivity)
    @Delete suspend fun delete(trip: Trip)
    @Delete suspend fun delete(reservation: Reservation)
    @Delete suspend fun delete(place: Place)
    @Delete suspend fun delete(plan: DayPlan)
    @Delete suspend fun delete(activity: PlanActivity)
}

@Database(entities = [Trip::class, Reservation::class, Place::class, DayPlan::class, PlanActivity::class], version = 2, exportSchema = true)
abstract class TripDatabase : RoomDatabase() {
    abstract fun dao(): TripDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE places ADD COLUMN googlePlaceId TEXT")
            }
        }
    }
}
