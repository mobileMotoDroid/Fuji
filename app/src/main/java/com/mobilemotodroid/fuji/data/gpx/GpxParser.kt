package com.mobilemotodroid.fuji.data.gpx

import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.time.OffsetDateTime
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParserFactory

/**
 * GPX 1.0 / 1.1 の読み込み（IF-04）。
 * Android と JVM の両方にある SAX を使うので、ユニットテストでもそのまま動く。
 */
object GpxParser {

    @Throws(GpxParseException::class)
    fun parse(input: InputStream): GpxData {
        val handler = Handler()
        try {
            val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
            // 外部エンティティを読み込まない（XXE 対策）。未対応の実装では無視する。
            runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            factory.newSAXParser().parse(input, handler)
        } catch (e: SAXException) {
            throw GpxParseException("GPX ファイルの形式が正しくありません。", e)
        } catch (e: ParserConfigurationException) {
            throw GpxParseException("GPX ファイルを読み込めませんでした。", e)
        } catch (e: IOException) {
            throw GpxParseException("GPX ファイルを読み込めませんでした。", e)
        }

        if (!handler.sawGpxRoot) {
            throw GpxParseException("GPX ファイルではありません（gpx 要素がありません）。")
        }
        val data = GpxData(
            name = handler.name?.trim()?.takeIf { it.isNotEmpty() },
            trackSegments = handler.segments.filter { it.isNotEmpty() },
            routePoints = handler.routePoints,
        )
        if (data.routeGeometry().isEmpty()) {
            throw GpxParseException("GPX にトラック（trk）またはルート（rte）の点がありません。")
        }
        return data
    }

    private class Handler : DefaultHandler() {
        var sawGpxRoot = false
        var name: String? = null
        val segments = mutableListOf<MutableList<GpxPoint>>()
        val routePoints = mutableListOf<GpxPoint>()

        private val path = ArrayDeque<String>()
        private val text = StringBuilder()
        private var pendingLat: Double? = null
        private var pendingLon: Double? = null
        private var pendingEle: Double? = null
        private var pendingTime: Long? = null

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
            val element = elementName(localName, qName)
            if (path.isEmpty()) {
                if (element != "gpx") throw SAXException("root element is not gpx")
                sawGpxRoot = true
            }
            when (element) {
                "trkseg" -> segments += mutableListOf<GpxPoint>()
                "trkpt", "rtept" -> {
                    pendingLat = attributes.coordinate("lat", -90.0..90.0)
                    pendingLon = attributes.coordinate("lon", -180.0..180.0)
                    pendingEle = null
                    pendingTime = null
                }
            }
            path.addLast(element)
            text.setLength(0)
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            text.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            val element = elementName(localName, qName)
            val parent = path.elementAtOrNull(path.size - 2)
            val value = text.toString().trim()
            when (element) {
                "ele" -> if (parent == "trkpt" || parent == "rtept") pendingEle = value.toDoubleOrNull()
                "time" -> if (parent == "trkpt" || parent == "rtept") pendingTime = parseTime(value)
                "name" -> if (name == null && parent in setOf("metadata", "trk", "rte", "gpx")) name = value
                "trkpt" -> {
                    if (segments.isEmpty()) segments += mutableListOf<GpxPoint>()
                    segments.last() += buildPoint()
                }
                "rtept" -> routePoints += buildPoint()
            }
            path.removeLastOrNull()
            text.setLength(0)
        }

        private fun buildPoint() = GpxPoint(
            latitude = pendingLat ?: throw SAXException("lat is missing"),
            longitude = pendingLon ?: throw SAXException("lon is missing"),
            elevationM = pendingEle,
            timeMs = pendingTime,
        )

        private fun elementName(localName: String?, qName: String?): String =
            localName?.takeIf { it.isNotEmpty() } ?: qName.orEmpty().substringAfter(':')

        private fun Attributes.coordinate(name: String, range: ClosedFloatingPointRange<Double>): Double {
            val raw = getValue(name) ?: getValue("", name) ?: throw SAXException("$name is missing")
            val v = raw.trim().toDoubleOrNull() ?: throw SAXException("$name is not a number")
            if (v !in range) throw SAXException("$name is out of range")
            return v
        }

        private fun parseTime(value: String): Long? =
            runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
                ?: runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
    }
}
