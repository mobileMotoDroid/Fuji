package com.example.traillog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoUtilsTest {

    @Test
    fun distance_oneDegreeLatitude_isAbout111km() {
        val d = GeoUtils.distanceM(35.0, 138.0, 36.0, 138.0)
        assertEquals(111_195.0, d, 50.0)
    }

    @Test
    fun distance_knownPair_matchesReference() {
        // 南北 約 2.75 km・東西 約 0.65 km 離れた 2 点：直線でおよそ 2.8 km
        val d = GeoUtils.distanceM(35.3606, 138.7274, 35.3359, 138.7346)
        assertEquals(2_830.0, d, 60.0)
    }

    @Test
    fun polylineLength_sumsSegments() {
        val points = listOf(GeoPoint(35.0, 138.0), GeoPoint(35.001, 138.0), GeoPoint(35.002, 138.0))
        assertEquals(222.4, GeoUtils.polylineLengthM(points), 0.5)
    }

    @Test
    fun distanceToPolyline_pointBesideSegment_isPerpendicularDistance() {
        val line = listOf(GeoPoint(35.0, 138.0), GeoPoint(35.01, 138.0))
        // 線の中央から東に約 91 m（経度 0.001 度 × cos35°）
        val d = GeoUtils.distanceToPolylineM(GeoPoint(35.005, 138.001), line)
        assertEquals(91.1, d, 1.0)
    }

    @Test
    fun distanceToPolyline_pointBeyondEnd_isDistanceToEndpoint() {
        val line = listOf(GeoPoint(35.0, 138.0), GeoPoint(35.001, 138.0))
        val d = GeoUtils.distanceToPolylineM(GeoPoint(35.002, 138.0), line)
        assertEquals(111.2, d, 1.0)
    }

    @Test
    fun distanceToPolyline_emptyLine_isInfinite() {
        assertTrue(GeoUtils.distanceToPolylineM(GeoPoint(35.0, 138.0), emptyList()).isInfinite())
    }

    @Test
    fun resample_keepsEndpointsAndSpacing() {
        val line = listOf(GeoPoint(35.0, 138.0), GeoPoint(35.01, 138.0)) // 約 1112 m
        val result = GeoUtils.resample(line, 200.0)
        assertEquals(line.first(), result.first())
        assertEquals(line.last(), result.last())
        assertEquals(7, result.size) // 0,200,400,600,800,1000,終点
    }

    @Test
    fun expandedByMeters_growsBothDirections() {
        val b = GeoBounds.around(GeoPoint(35.0, 138.0), 1_000.0)
        assertEquals(1_000.0, GeoUtils.distanceM(35.0, 138.0, b.north, 138.0), 5.0)
        assertEquals(1_000.0, GeoUtils.distanceM(35.0, 138.0, 35.0, b.east), 5.0)
    }
}
