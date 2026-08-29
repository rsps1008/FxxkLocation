package com.rsps1008.fxxklocation.data.state

import com.rsps1008.fxxklocation.data.model.LocationData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class MockLocationRuntimeStateTest {
    @Before
    fun setUp() {
        MockLocationRuntimeState.clear()
        MockLocationRuntimeState.clearStopRequest()
    }

    @After
    fun tearDown() {
        MockLocationRuntimeState.clear()
        MockLocationRuntimeState.clearStopRequest()
    }

    @Test
    fun updatePublishesLatestLocation() {
        val location = LocationData(latitude = 25.033, longitude = 121.5654, altitude = 3.0)

        MockLocationRuntimeState.update(location)

        assertEquals(location, MockLocationRuntimeState.currentLocation.value)
    }

    @Test
    fun clearRemovesCurrentLocation() {
        MockLocationRuntimeState.update(LocationData(25.033, 121.5654, 3.0))

        MockLocationRuntimeState.clear()

        assertNull(MockLocationRuntimeState.currentLocation.value)
    }

    @Test
    fun stopRequestCanBeSetAndCleared() {
        MockLocationRuntimeState.requestStop()
        assertEquals(true, MockLocationRuntimeState.isStopRequested.value)

        MockLocationRuntimeState.clearStopRequest()

        assertEquals(false, MockLocationRuntimeState.isStopRequested.value)
    }
}
