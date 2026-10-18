package com.mobilemotodroid.fuji.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ElevationAndTrackTest {

    @Test
    fun elevationGain_ignoresNoiseBelowThreshold() {
        val calc = ElevationGainCalculator(5.0)
        // ±3 m の揺らぎは加算しない
        listOf(1000.0, 1003.0, 998.0, 1002.0, 999.0).forEach(calc::add)
        assertEquals(0.0, calc.ascentM, 1e-9)
        assertEquals(0.0, calc.descentM, 1e-9)
    }

    @Test
    fun elevationGain_countsRealClimbAndDescent() {
        val calc = ElevationGainCalculator(5.0)
        listOf(1000.0, 1010.0, 1020.0, 1015.0, 1005.0, 1030.0).forEach(calc::add)
        assertEquals(45.0, calc.ascentM, 1e-9) // 1000→1020 (+20), 1005→1030 (+25)
        assertEquals(15.0, calc.descentM, 1e-9) // 1020→1005 (-15)
    }

    @Test
    fun trackAccumulator_doesNotAddDistanceAcrossSegments() {
        val acc = TrackAccumulator()
        acc.add(TrackSample(0, 35.0, 138.0, 100.0, segment = 0))
        acc.add(TrackSample(1_000, 35.001, 138.0, 110.0, segment = 0)) // +111 m
        acc.add(TrackSample(2_000, 35.010, 138.0, 120.0, segment = 1)) // 区間が変わったので加算しない
        acc.add(TrackSample(3_000, 35.011, 138.0, 130.0, segment = 1)) // +111 m
        assertEquals(222.4, acc.distanceM, 1.0)
        assertEquals(130.0, acc.maxAltitudeM!!, 1e-9)
        assertEquals(100.0, acc.minAltitudeM!!, 1e-9)
        assertEquals(30.0, acc.ascentM, 1e-9)
        assertEquals(4, acc.pointCount)
    }

    @Test
    fun trackAccumulator_withoutAltitude_hasNoMinMax() {
        val acc = TrackAccumulator()
        acc.add(TrackSample(0, 35.0, 138.0, null, 0))
        assertNull(acc.maxAltitudeM)
        assertNull(acc.minAltitudeM)
    }

    @Test
    fun altitudeFuser_withoutBarometer_returnsGps() {
        val fuser = AltitudeFuser()
        assertEquals(1234.0, fuser.fuse(1234.0, 5f, null)!!, 1e-9)
    }

    @Test
    fun altitudeFuser_learnsOffsetAndFollowsBarometer() {
        val fuser = AltitudeFuser(alpha = 0.5)
        // 気圧高度は真値より 100 m 低いと仮定
        fuser.fuse(1000.0, 5f, 900.0)
        // GPS が使えなくても、学習したオフセットで気圧高度を補正する
        val fused = fuser.fuse(null, null, 950.0)!!
        assertEquals(1050.0, fused, 1e-9)
    }

    @Test
    fun altitudeFuser_ignoresInaccurateGps() {
        val fuser = AltitudeFuser(alpha = 1.0, maxGpsVerticalAccuracyM = 30f)
        fuser.fuse(1000.0, 5f, 900.0) // offset = 100
        fuser.fuse(2000.0, 80f, 900.0) // 精度が悪いので無視
        assertEquals(1000.0, fuser.fuse(null, null, 900.0)!!, 1e-9)
    }
}
