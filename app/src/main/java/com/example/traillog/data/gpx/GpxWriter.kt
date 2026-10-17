package com.example.traillog.data.gpx

import java.io.Writer
import java.time.Instant
import java.util.Locale

/** GPX 1.1 の書き出し（FR-ACT-09, IF-04）。 */
object GpxWriter {

    fun writeTrack(
        out: Writer,
        name: String,
        description: String?,
        segments: List<List<GpxPoint>>,
        creator: String = "TrailLog",
    ) {
        out.write("""<?xml version="1.0" encoding="UTF-8"?>""")
        out.write("\n")
        out.write(
            """<gpx version="1.1" creator="${escape(creator)}" xmlns="http://www.topografix.com/GPX/1/1" """ +
                """xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" """ +
                """xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">""",
        )
        out.write("\n  <metadata>\n    <name>${escape(name)}</name>\n")
        description?.takeIf { it.isNotBlank() }?.let { out.write("    <desc>${escape(it)}</desc>\n") }
        segments.firstOrNull()?.firstOrNull()?.timeMs?.let { out.write("    <time>${Instant.ofEpochMilli(it)}</time>\n") }
        out.write("  </metadata>\n")
        out.write("  <trk>\n    <name>${escape(name)}</name>\n")
        for (segment in segments) {
            if (segment.isEmpty()) continue
            out.write("    <trkseg>\n")
            for (p in segment) {
                out.write("      <trkpt lat=\"${coord(p.latitude)}\" lon=\"${coord(p.longitude)}\">")
                p.elevationM?.let { out.write("<ele>${String.format(Locale.ROOT, "%.1f", it)}</ele>") }
                p.timeMs?.let { out.write("<time>${Instant.ofEpochMilli(it)}</time>") }
                out.write("</trkpt>\n")
            }
            out.write("    </trkseg>\n")
        }
        out.write("  </trk>\n</gpx>\n")
        out.flush()
    }

    private fun coord(v: Double) = String.format(Locale.ROOT, "%.7f", v)

    private fun escape(s: String): String = buildString(s.length) {
        for (c in s) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> if (c >= ' ' || c == '\n' || c == '\t' || c == '\r') append(c)
            }
        }
    }
}
