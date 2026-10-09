package com.famiglia.tripcompanion

import android.app.Application
import androidx.room.Room
import com.famiglia.tripcompanion.data.TripDatabase
import com.famiglia.tripcompanion.data.TripRepository

class TripApplication : Application() {
    val database by lazy { Room.databaseBuilder(this, TripDatabase::class.java, "trip-companion.db").build() }
    val repository by lazy { TripRepository(database) }
}
