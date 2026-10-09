package com.famiglia.tripcompanion

import com.famiglia.tripcompanion.maps.MapLocation
import com.famiglia.tripcompanion.maps.PlaceLinks
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlaceLinksTest {
    @Test fun mapActionsIdentifyTheSelectedPlaceAndWebsiteActionsRejectNonWebSchemes() {
        val place = MapLocation("id with&symbols", "Café", "Bari", 41.1, 16.9)
        val directions = PlaceLinks.directions(place)
        assertEquals(place.id, directions.getQueryParameter("destination_place_id"))
        assertEquals("41.1,16.9", directions.getQueryParameter("destination"))
        assertEquals(place.id, PlaceLinks.view(place).getQueryParameter("query_place_id"))
        assertNull(PlaceLinks.website("javascript:alert(1)"))
        assertNull(PlaceLinks.website("file:///private-notes"))
        assertEquals("example.com", PlaceLinks.website("https://example.com/place")?.host)
    }
}
