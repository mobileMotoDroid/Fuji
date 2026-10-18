package com.mobilemotodroid.fuji.data.gpx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.StringWriter

class GpxTest {

    private fun parse(xml: String) = GpxParser.parse(xml.byteInputStream())

    @Test
    fun parsesGpx11Track() {
        val data = parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1">
              <metadata><name>富士山 富士宮ルート</name></metadata>
              <trk><name>track</name>
                <trkseg>
                  <trkpt lat="35.3359" lon="138.7346"><ele>2380.5</ele><time>2026-08-01T05:00:00Z</time></trkpt>
                  <trkpt lat="35.3400" lon="138.7330"><ele>2500</ele><time>2026-08-01T14:30:00+09:00</time></trkpt>
                </trkseg>
                <trkseg>
                  <trkpt lat="35.3606" lon="138.7274"/>
                </trkseg>
              </trk>
            </gpx>
            """.trimIndent(),
        )
        assertEquals("富士山 富士宮ルート", data.name)
        assertEquals(2, data.trackSegments.size)
        assertEquals(3, data.routeGeometry().size)
        val first = data.trackSegments[0][0]
        assertEquals(35.3359, first.latitude, 1e-9)
        assertEquals(2380.5, first.elevationM!!, 1e-9)
        assertEquals(1_785_560_400_000L, first.timeMs) // 2026-08-01T05:00:00Z
        assertEquals(1_785_562_200_000L, data.trackSegments[0][1].timeMs) // 05:30Z
        assertNull(data.trackSegments[1][0].elevationM)
    }

    @Test
    fun parsesGpx10Route() {
        val data = parse(
            """
            <gpx version="1.0" creator="test" xmlns="http://www.topografix.com/GPX/1/0">
              <name>route only</name>
              <rte>
                <rtept lat="35.0" lon="138.0"><ele>100</ele></rtept>
                <rtept lat="35.1" lon="138.1"/>
              </rte>
            </gpx>
            """.trimIndent(),
        )
        assertEquals("route only", data.name)
        assertTrue(data.trackSegments.isEmpty())
        assertEquals(2, data.routeGeometry().size)
    }

    @Test
    fun rejectsBrokenXml() {
        assertParseError("<gpx><trk><trkseg><trkpt lat=\"35\" lon=\"138\"></trkseg></gpx>")
    }

    @Test
    fun rejectsNonGpxRoot() {
        assertParseError("<kml><Placemark/></kml>")
    }

    @Test
    fun rejectsGpxWithoutPoints() {
        assertParseError("<gpx version=\"1.1\"><metadata><name>x</name></metadata></gpx>")
    }

    @Test
    fun rejectsOutOfRangeCoordinates() {
        assertParseError("<gpx><trk><trkseg><trkpt lat=\"135\" lon=\"138\"/></trkseg></trk></gpx>")
    }

    @Test
    fun writerOutputCanBeParsedBack() {
        val segments = listOf(
            listOf(
                GpxPoint(35.1234567, 138.7654321, 1500.0, 1_785_560_400_000L),
                GpxPoint(35.1240000, 138.7660000, 1510.5, 1_785_560_410_000L),
            ),
            listOf(GpxPoint(35.2, 138.8, null, null)),
        )
        val out = StringWriter()
        GpxWriter.writeTrack(out, "テスト <&> \"山行\"", "メモ", segments)
        val xml = out.toString()
        assertTrue(xml.contains("version=\"1.1\""))
        assertTrue(xml.contains("&lt;&amp;&gt;"))

        val back = parse(xml)
        assertEquals("テスト <&> \"山行\"", back.name)
        assertEquals(2, back.trackSegments.size)
        assertEquals(segments[0][0], back.trackSegments[0][0])
        assertEquals(1510.5, back.trackSegments[0][1].elevationM!!, 1e-9)
    }

    private fun assertParseError(xml: String) {
        try {
            parse(xml)
            fail("GpxParseException expected")
        } catch (e: GpxParseException) {
            assertTrue(e.message!!.isNotBlank())
        }
    }
}
