package com.mobilemotodroid.fuji.ui.common

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mobilemotodroid.fuji.domain.GeoBounds
import com.mobilemotodroid.fuji.domain.GeoPoint
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.MultiLineString
import org.maplibre.geojson.Point
import kotlin.coroutines.resume

/** スタイルの読み込みが終わった地図。 */
class MapHandle(val map: MapLibreMap, val style: Style)

/** 日本全体が見える初期位置。 */
val JAPAN_CENTER = LatLng(36.2, 138.25)

/**
 * MapLibre の MapView を Compose で使うためのラッパー。
 * 画面のライフサイクルに合わせて MapView の onStart / onResume などを呼ぶ。
 */
@Composable
fun MapLibreView(
    styleUri: String,
    modifier: Modifier = Modifier,
    onMapReady: (MapHandle) -> Unit,
) {
    val context = LocalContext.current
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (!started) { mapView.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> if (!resumed) { mapView.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> if (resumed) { mapView.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> if (started) { mapView.onStop(); started = false }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }

    val currentOnMapReady by rememberUpdatedState(onMapReady)
    LaunchedEffect(mapView, styleUri) {
        val map = mapView.awaitMap()
        map.uiSettings.isLogoEnabled = false
        map.uiSettings.isCompassEnabled = true
        map.setMaxZoomPreference(MAX_DISPLAY_ZOOM)
        map.setStyle(Style.Builder().fromUri(styleUri)) { style ->
            MapLayers.install(style)
            currentOnMapReady(MapHandle(map, style))
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

/** 地理院タイル（最大 z18）より少し拡大できるようにする。 */
private const val MAX_DISPLAY_ZOOM = 18.5

suspend fun MapView.awaitMap(): MapLibreMap = suspendCancellableCoroutine { cont ->
    getMapAsync { map -> if (cont.isActive) cont.resume(map) }
}

/** ルート・軌跡・写真の表示レイヤー。 */
object MapLayers {
    private const val ROUTE_SOURCE = "route-source"
    private const val ROUTE_LAYER = "route-layer"
    private const val TRACK_SOURCE = "track-source"
    private const val TRACK_LAYER = "track-layer"
    private const val PHOTO_SOURCE = "photo-source"
    const val PHOTO_LAYER = "photo-layer"
    const val PHOTO_ID = "photoId"

    fun install(style: Style) {
        if (style.getSource(ROUTE_SOURCE) == null) {
            style.addSource(GeoJsonSource(ROUTE_SOURCE))
            style.addLayer(
                LineLayer(ROUTE_LAYER, ROUTE_SOURCE).withProperties(
                    PropertyFactory.lineColor("#7B3FE4"),
                    PropertyFactory.lineWidth(6f),
                    PropertyFactory.lineOpacity(0.75f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
            )
        }
        if (style.getSource(TRACK_SOURCE) == null) {
            style.addSource(GeoJsonSource(TRACK_SOURCE))
            style.addLayer(
                LineLayer(TRACK_LAYER, TRACK_SOURCE).withProperties(
                    PropertyFactory.lineColor("#E53935"),
                    PropertyFactory.lineWidth(4f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
            )
        }
        if (style.getSource(PHOTO_SOURCE) == null) {
            style.addSource(GeoJsonSource(PHOTO_SOURCE))
            style.addLayer(
                CircleLayer(PHOTO_LAYER, PHOTO_SOURCE).withProperties(
                    PropertyFactory.circleRadius(8f),
                    PropertyFactory.circleColor("#1E88E5"),
                    PropertyFactory.circleStrokeColor("#FFFFFF"),
                    PropertyFactory.circleStrokeWidth(2.5f),
                ),
            )
        }
    }

    /** FR-RTE-03：予定ルート。 */
    fun setRoute(style: Style, points: List<GeoPoint>) {
        val features = if (points.size >= 2) {
            listOf(Feature.fromGeometry(LineString.fromLngLats(points.map { it.toPoint() })))
        } else {
            emptyList()
        }
        style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    /** FR-STA-06 / FR-ACT-02：軌跡（一時停止で区切られた区間ごと）。 */
    fun setTrack(style: Style, segments: List<List<GeoPoint>>) {
        val lines = segments.filter { it.size >= 2 }.map { seg -> seg.map { it.toPoint() } }
        val features = if (lines.isEmpty()) emptyList() else listOf(Feature.fromGeometry(MultiLineString.fromLngLats(lines)))
        style.getSourceAs<GeoJsonSource>(TRACK_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    /** FR-ACT-06：写真のピン。 */
    fun setPhotos(style: Style, photos: List<Pair<Long, GeoPoint>>) {
        val features = photos.map { (id, p) ->
            Feature.fromGeometry(p.toPoint()).apply { addNumberProperty(PHOTO_ID, id) }
        }
        style.getSourceAs<GeoJsonSource>(PHOTO_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    private fun GeoPoint.toPoint(): Point = Point.fromLngLat(longitude, latitude)
}

/** FR-MAP-03：現在地（精度円・向き付き）を表示する。位置は ForegroundLocationSource から渡す。 */
@SuppressLint("MissingPermission")
fun MapHandle.enableLocationComponent(context: Context) {
    val lc = map.locationComponent
    if (!lc.isLocationComponentActivated) {
        lc.activateLocationComponent(
            LocationComponentActivationOptions.builder(context, style)
                .useDefaultLocationEngine(false)
                .build(),
        )
    }
    lc.isLocationComponentEnabled = true
    lc.renderMode = RenderMode.COMPASS
}

fun MapHandle.updateLocation(location: Location) {
    val lc = map.locationComponent
    if (lc.isLocationComponentActivated) lc.forceLocationUpdate(location)
}

/** FR-MAP-04 / FR-MAP-05：現在地に追従する。北固定か進行方向固定かを選べる。 */
fun MapHandle.followLocation(headingUp: Boolean) {
    val lc = map.locationComponent
    if (!lc.isLocationComponentActivated || !lc.isLocationComponentEnabled) return
    lc.cameraMode = if (headingUp) CameraMode.TRACKING_COMPASS else CameraMode.TRACKING_GPS_NORTH
    if (map.cameraPosition.zoom < 13.0) lc.zoomWhileTracking(15.0)
}

fun MapHandle.fitTo(points: List<GeoPoint>, paddingPx: Int) {
    val bounds = GeoBounds.of(points) ?: return
    if (bounds.north - bounds.south < 1e-6 && bounds.east - bounds.west < 1e-6) {
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(bounds.north, bounds.east), 15.0))
        return
    }
    map.moveCamera(
        CameraUpdateFactory.newLatLngBounds(
            LatLngBounds.from(bounds.north, bounds.east, bounds.south, bounds.west),
            paddingPx,
        ),
    )
}
