package com.tamalut.radio.feature.radio

import com.tamalut.radio.core.model.RadioStation
import com.tamalut.radio.core.model.StationId
import com.tamalut.radio.core.model.StreamEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveRadioVisibilityPolicyTest {
    @Test
    fun activeStationInRenderedListReturnsItsIndex() {
        assertEquals(2, activeStationAutoScrollIndex(stations, "three", autoScrollEnabled = true))
    }

    @Test
    fun activeStationOutsideRenderedListHasNoScrollTarget() {
        assertNull(activeStationAutoScrollIndex(stations, "missing", autoScrollEnabled = true))
        assertNull(activeStationAutoScrollIndex(stations, null, autoScrollEnabled = true))
    }

    @Test
    fun activeSearchSuppressesAutomaticScrollTarget() {
        assertNull(activeStationAutoScrollIndex(stations, "two", autoScrollEnabled = false))
    }

    private companion object {
        val stations = listOf("one", "two", "three").map { id ->
  RadioStation(
      id = StationId(id),
      name = id,
      primaryStream = StreamEndpoint("https://example.com/$id.mp3"),
  )
        }
    }
}
