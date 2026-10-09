package com.famiglia.tripcompanion

import android.app.Application
import androidx.room.Room
import com.famiglia.tripcompanion.data.TripDatabase
import com.famiglia.tripcompanion.data.TripRepository
import com.famiglia.tripcompanion.planning.NanoAi
import com.famiglia.tripcompanion.planning.OnDeviceAi
import com.famiglia.tripcompanion.maps.PlaceLookup

class TripApplication : Application() {
    var dayAi: OnDeviceAi = NanoAi()
    // Controlled UI tests replace the provider before launching the activity.
    var placeLookupOverride: PlaceLookup? = null
    val database by lazy {
        Room.databaseBuilder(this, TripDatabase::class.java, "trip-companion.db")
            .addMigrations(TripDatabase.MIGRATION_1_2, TripDatabase.MIGRATION_2_3).build()
    }
    val repository by lazy { TripRepository(database) }
}
