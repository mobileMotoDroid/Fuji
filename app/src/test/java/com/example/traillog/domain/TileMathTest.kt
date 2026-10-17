package com.example.traillog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileMathTest {

    @Test
    fun tileCoordinates_matchGsiExample() {
        // 東京駅付近 z=16 → x=58211, y=25806
        assertEquals(58211, TileMath.lonToTileX(139.7671, 16))
        assertEquals(25806, TileMath.latToTileY(35.6812, 16))
    }

    @Test
    fun countTiles_singlePoint_isOnePerZoom() {
        val b = GeoBounds(35.0, 138.0, 35.0, 138.0)
        assertEquals(7L, TileMath.countTiles(b, 10, 16))
    }

    @Test
    fun countTilesCovering_deduplicatesOverlaps() {
        val box = GeoBounds.around(GeoPoint(35.36, 138.73), 500.0)
        val single = TileMath.countTiles(box, 10, 15)
        val twice = TileMath.countTilesCovering(listOf(box, box), 10, 15)
        assertEquals(single, twice)
    }

    @Test
    fun countTilesCovering_stopsAtLimit() {
        val box = GeoBounds(35.0, 138.0, 36.0, 139.0)
        val count = TileMath.countTilesCovering(listOf(box), 10, 16, limit = 100)
        assertEquals(101L, count)
    }

    @Test
    fun metersPerDp_atEquatorZoom0() {
        // 512 dp で地球 1 周（約 40,075 km）
        assertEquals(78_271.5, TileMath.metersPerDp(0.0, 0.0), 1.0)
        assertTrue(TileMath.metersPerDp(35.0, 15.0) < 2.0)
    }
}
