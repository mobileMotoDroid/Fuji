package com.example.traillog.domain

import com.example.traillog.domain.DeviationDetector.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviationDetectorTest {

    @Test
    fun warnsOnlyAfterDuration() {
        val d = DeviationDetector(thresholdM = 50.0, durationMs = 30_000)
        assertNull(d.update(80.0, 0))
        assertNull(d.update(80.0, 20_000))
        assertEquals(Event.DEVIATED, d.update(80.0, 30_000))
        assertTrue(d.isDeviated)
        // 逸脱中は繰り返し通知しない
        assertNull(d.update(90.0, 40_000))
    }

    @Test
    fun shortExcursionIsIgnored() {
        val d = DeviationDetector(thresholdM = 50.0, durationMs = 30_000)
        assertNull(d.update(80.0, 0))
        assertNull(d.update(10.0, 10_000)) // 30 秒以内に戻った
        assertNull(d.update(80.0, 20_000))
        assertNull(d.update(80.0, 40_000)) // 20 秒しかたっていない
        assertFalse(d.isDeviated)
    }

    @Test
    fun reportsReturn() {
        val d = DeviationDetector(thresholdM = 50.0, durationMs = 0)
        assertEquals(Event.DEVIATED, d.update(60.0, 0))
        assertEquals(Event.RETURNED, d.update(20.0, 1_000))
        assertFalse(d.isDeviated)
    }

    @Test
    fun endToEnd_withPolylineDistance() {
        val route = listOf(GeoPoint(35.0, 138.0), GeoPoint(35.01, 138.0))
        val d = DeviationDetector(thresholdM = 50.0, durationMs = 0)
        val onRoute = GeoUtils.distanceToPolylineM(GeoPoint(35.005, 138.0002), route) // 約 18 m
        val offRoute = GeoUtils.distanceToPolylineM(GeoPoint(35.005, 138.001), route) // 約 91 m
        assertNull(d.update(onRoute, 0))
        assertEquals(Event.DEVIATED, d.update(offRoute, 1_000))
    }
}
