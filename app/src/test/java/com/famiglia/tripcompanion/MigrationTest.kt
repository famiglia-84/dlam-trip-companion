package com.famiglia.tripcompanion

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.famiglia.tripcompanion.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MigrationTest {
    @Test fun upgradesActualVersionOneSchemaWithoutLosingAnyTravelRecords() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-${UUID.randomUUID()}.db"
        val schema = javaClass.classLoader!!.getResourceAsStream("com.famiglia.tripcompanion.data.TripDatabase/1.json")!!
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                fun sql(value: String) = value.replace("\${TABLE_NAME}", entity.getString("tableName"))
                old.execSQL(sql(entity.getString("createSql")))
                val indices = entity.optJSONArray("indices")
                if (indices != null) for (j in 0 until indices.length()) old.execSQL(sql(indices.getJSONObject(j).getString("createSql")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO trips VALUES(1, 'Bari', '2026-10-10', '2026-10-14', 'Private trip notes', '')")
            old.execSQL("INSERT INTO reservations VALUES(1, 1, 'Hotel', 'HOTEL', '2026-10-10', '', '', '', 'ABC123', 'Old town', 'Private booking notes')")
            old.execSQL("INSERT INTO places VALUES(1, 1, 'Castle', 'LANDMARK', 'Bari', 'Private place notes')")
            old.execSQL("INSERT INTO day_plans VALUES(1, 1, '2026-10-10', 'Explore', 'Day notes')")
            old.execSQL("INSERT INTO activities (id, planId, title, time, notes, position, placeId) VALUES(1, 1, 'Castle visit', '10:00', 'Activity notes', 0, 1)")
            old.version = 1
        }
        val upgraded = Room.databaseBuilder(context, TripDatabase::class.java, name)
            .addMigrations(TripDatabase.MIGRATION_1_2).allowMainThreadQueries().build()
        try {
            val repo = TripRepository(upgraded)
            val data = repo.data.first()
            assertEquals("Private trip notes", data.trips.single().notes)
            assertEquals("ABC123", data.reservations.single().confirmation)
            assertEquals("Private place notes", data.places.single().notes)
            assertNull(data.places.single().googlePlaceId)
            assertEquals("Explore", data.plans.single().title)
            assertEquals(1L, data.activities.single().placeId)
            repo.save(data.places.single().copy(googlePlaceId = "google-id"))
            assertEquals("google-id", repo.data.first().places.single().googlePlaceId)
        } finally { upgraded.close(); context.deleteDatabase(name) }
    }
}
