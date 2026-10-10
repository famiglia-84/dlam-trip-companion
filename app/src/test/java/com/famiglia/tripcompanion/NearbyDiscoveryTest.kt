package com.famiglia.tripcompanion

import com.famiglia.tripcompanion.maps.MapArea
import org.junit.Assert.*
import org.junit.Test

class NearbyDiscoveryTest {
    @Test fun areasHandleDatelineCrossingAndOnlyMeaningfulChanges() {
        assertEquals(222.39, MapArea.distanceMeters(0.0, 179.999, 0.0, -179.999), 1.0)
        val area = MapArea(41.1, 16.9, 1000.0)
        assertFalse(area.differsFrom(area.copy(latitude = 41.1001)))
        assertFalse(area.differsFrom(area.copy(radiusMeters = 1010.0)))
        assertTrue(area.differsFrom(area.copy(latitude = 41.11)))
        assertTrue(area.differsFrom(area.copy(radiusMeters = 1500.0)))
        assertTrue(MapArea(90.0, 180.0, 50_000.0).searchable)
        assertFalse(MapArea(41.1, 16.9, Double.POSITIVE_INFINITY).searchable)
        assertFalse(MapArea(91.0, 16.9, 1000.0).searchable)
    }
}
