package com.famiglia.tripcompanion

import android.app.Application
import androidx.room.Room
import com.famiglia.tripcompanion.data.TripDatabase
import com.famiglia.tripcompanion.data.TripRepository
import com.famiglia.tripcompanion.planning.NanoAi
import com.famiglia.tripcompanion.planning.OnDeviceAi

class TripApplication : Application() {
    var dayAi: OnDeviceAi = NanoAi()
    val database by lazy {
        Room.databaseBuilder(this, TripDatabase::class.java, "trip-companion.db")
            .addMigrations(TripDatabase.MIGRATION_1_2).build()
    }
    val repository by lazy { TripRepository(database) }
}
