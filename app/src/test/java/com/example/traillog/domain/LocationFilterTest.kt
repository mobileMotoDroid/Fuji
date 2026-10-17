package com.example.traillog.domain

import com.example.traillog.domain.LocationFilter.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationFilterTest {

    private fun fix(tSec: Long, lat: Double, lon: Double = 138.0, acc: Float = 10f) =
        LocationFix(tSec * 1000, lat, lon, acc)

    @Test
    fun rejectsPoorAccuracy() {
        val f = LocationFilter(maxAccuracyM = 50f)
        assertEquals(Decision.REJECT_ACCURACY, f.evaluate(fix(0, 35.0, acc = 60f)))
        assertEquals(Decision.ACCEPT, f.evaluate(fix(1, 35.0, acc = 40f)))
    }

    @Test
    fun rejectsImpossibleSpeed() {
        val f = LocationFilter(maxSpeedKmh = 30.0)
        assertEquals(Decision.ACCEPT, f.evaluate(fix(0, 35.0)))
        // 10 秒で約 1.1 km（時速 400 km）→ 除外
        assertEquals(Decision.REJECT_SPEED, f.evaluate(fix(10, 35.01)))
        // 歩行速度（10 秒で約 11 m）→ 採用
        assertEquals(Decision.ACCEPT, f.evaluate(fix(20, 35.0001)))
    }

    @Test
    fun rejectsDuplicateTimestamp() {
        val f = LocationFilter()
        f.evaluate(fix(0, 35.0))
        assertEquals(Decision.REJECT_DUPLICATE, f.evaluate(fix(0, 35.00001)))
    }

    @Test
    fun thinsPointsWhileStationary() {
        val f = LocationFilter(
            stationaryRadiusM = 10.0,
            stationaryThinAfterMs = 5 * 60_000L,
            stationaryKeepIntervalMs = 5 * 60_000L,
        )
        assertEquals(Decision.ACCEPT, f.evaluate(fix(0, 35.0)))
        // 5 分未満はそのまま記録する
        assertEquals(Decision.ACCEPT, f.evaluate(fix(60, 35.00001)))
        assertEquals(Decision.ACCEPT, f.evaluate(fix(290, 35.00002)))
        // 5 分を超えて止まっている → 前の採用点から 5 分たつまで間引く
        assertEquals(Decision.THINNED, f.evaluate(fix(310, 35.00001)))
        assertEquals(Decision.THINNED, f.evaluate(fix(500, 35.00001)))
        assertEquals(Decision.ACCEPT, f.evaluate(fix(600, 35.00001)))
        // 動き出したらすぐ記録する
        assertEquals(Decision.ACCEPT, f.evaluate(fix(610, 35.0003)))
    }

    @Test
    fun newSegment_resetsSpeedCheck() {
        val f = LocationFilter()
        f.evaluate(fix(0, 35.0))
        f.startNewSegment()
        // 一時停止中に車で移動しても、再開後の最初の点は採用する
        assertEquals(Decision.ACCEPT, f.evaluate(fix(10, 35.1)))
    }
}
