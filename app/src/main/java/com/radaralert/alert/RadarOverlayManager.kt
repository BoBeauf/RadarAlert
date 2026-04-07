package com.radaralert.alert

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.radaralert.R
import com.radaralert.data.SettingsRepository
import com.radaralert.location.ProximityChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class RadarOverlayManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var textView: View? = null
    private var mapView: View? = null
    private var osmMap: MapView? = null
    private var userMarker: Marker? = null
    private val radarMarkers = mutableListOf<Marker>()

    private var currentStyle = "text"

    private val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 0
        y = 80
    }

    fun showIdle() {
        if (!Settings.canDrawOverlays(context)) return
        scope.launch {
            val savedX = settingsRepository.overlayX.first()
            val savedY = settingsRepository.overlayY.first()
            currentStyle = settingsRepository.overlayStyle.first()
            mainHandler.post {
                layoutParams.x = savedX
                layoutParams.y = savedY
                ensureTextView()
                setIdleState()
            }
        }
    }

    fun showAlert(
        radars: List<ProximityChecker.NearbyRadar>,
        userSpeedKmh: Float?,
        userLat: Double? = null,
        userLng: Double? = null,
        bearing: Float? = null
    ) {
        mainHandler.post {
            scope.launch {
                val style = settingsRepository.overlayStyle.first()
                mainHandler.post {
                    if (style != currentStyle) {
                        dismissCurrentView()
                        currentStyle = style
                    }
                    when (style) {
                        "map" -> {
                            ensureMapView()
                            setMapAlertState(radars, userSpeedKmh, userLat, userLng, bearing)
                        }
                        else -> {
                            ensureTextView()
                            setTextAlertState(radars, userSpeedKmh)
                        }
                    }
                }
            }
        }
    }

    fun updateAlert(
        radars: List<ProximityChecker.NearbyRadar>,
        userSpeedKmh: Float?,
        userLat: Double? = null,
        userLng: Double? = null,
        bearing: Float? = null
    ) {
        val closest = radars.first()
        mainHandler.post {
            when (currentStyle) {
                "map" -> {
                    val map = osmMap ?: return@post
                    mapView?.findViewById<TextView>(R.id.mapTvSpeed)?.text =
                        closest.radar.speedLimit?.toString() ?: "?"
                    mapView?.findViewById<TextView>(R.id.mapTvDistance)?.text =
                        formatDistance(closest.distanceMeters)
                    userSpeedKmh?.let {
                        mapView?.findViewById<TextView>(R.id.mapTvUserSpeed)?.text = "${it.toInt()} km/h"
                    }
                    setCountBadge(mapView, R.id.mapTvCount, radars.size)
                    if (userLat != null && userLng != null) {
                        val userGeo = GeoPoint(userLat, userLng)
                        userMarker?.position = userGeo
                        map.controller.setCenter(userGeo)
                        bearing?.let { map.mapOrientation = -it }
                    }
                    updateRadarMarkers(radars, map)
                }
                else -> {
                    val view = textView ?: return@post
                    view.findViewById<TextView>(R.id.tvDistance).text =
                        formatDistance(closest.distanceMeters)
                    view.findViewById<TextView>(R.id.tvSpeed).text =
                        closest.radar.speedLimit?.toString() ?: "?"
                    userSpeedKmh?.let {
                        view.findViewById<TextView>(R.id.tvUserSpeed).text = "${it.toInt()} km/h"
                    }
                    setCountBadge(view, R.id.tvRadarCount, radars.size)
                }
            }
        }
    }

    fun backToIdle() {
        mainHandler.post {
            when (currentStyle) {
                "map" -> {
                    dismissMapView()
                    ensureTextView()
                    setIdleState()
                }
                else -> setIdleState()
            }
        }
    }

    fun dismiss() {
        mainHandler.post { dismissCurrentView() }
    }

    // ── Vues ──────────────────────────────────────────────────────────────

    private fun ensureTextView() {
        if (textView != null) return
        val view = LayoutInflater.from(context).inflate(R.layout.overlay_radar, null)
        attachDragListener(view)
        textView = view
        try {
            windowManager.addView(view, layoutParams)
        } catch (e: Exception) {
            textView = null
        }
    }

    private fun ensureMapView() {
        if (mapView != null) return
        dismissTextView()

        val density = context.resources.displayMetrics.density
        layoutParams.width = (150 * density).toInt()
        layoutParams.height = (210 * density).toInt()

        val view = LayoutInflater.from(context).inflate(R.layout.overlay_radar_map, null)
        val map = view.findViewById<MapView>(R.id.miniMap)

        try {
            listOf("Mapnik", "CartoDB Dark").forEach { name ->
                val f = java.io.File(Configuration.getInstance().osmdroidTileCache, name)
                if (f.exists()) f.deleteRecursively()
            }
        } catch (_: Exception) {}

        map.setTileSource(cartoVoyagerSource())
        map.setMultiTouchControls(false)
        map.controller.setZoom(17.0)
        osmMap = map

        userMarker = Marker(map).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = ContextCompat.getDrawable(context, R.drawable.ic_user_nav)
        }
        map.overlays.add(userMarker)

        // Inclinaison 3D style navigation
        map.rotationX = 30f
        map.cameraDistance = density * 2600f
        map.translationY = -(density * 12f)

        view.clipToOutline = true
        map.onResume()
        attachDragListener(view)
        mapView = view

        try {
            windowManager.addView(view, layoutParams)
        } catch (e: Exception) {
            map.onPause()
            mapView = null
            osmMap = null
        }
    }

    // ── États ─────────────────────────────────────────────────────────────

    private fun setIdleState() {
        val view = textView ?: return
        view.findViewById<LinearLayout>(R.id.layoutIdle).visibility = View.VISIBLE
        view.findViewById<LinearLayout>(R.id.layoutAlert).visibility = View.GONE
        try { windowManager.updateViewLayout(view, layoutParams) } catch (_: Exception) {}
    }

    private fun setTextAlertState(radars: List<ProximityChecker.NearbyRadar>, userSpeedKmh: Float?) {
        val view = textView ?: return
        val closest = radars.first()
        view.findViewById<LinearLayout>(R.id.layoutIdle).visibility = View.GONE
        view.findViewById<LinearLayout>(R.id.layoutAlert).visibility = View.VISIBLE
        view.findViewById<TextView>(R.id.tvType).text = formatType(closest.radar.type)
        view.findViewById<TextView>(R.id.tvSpeed).text = closest.radar.speedLimit?.toString() ?: "?"
        view.findViewById<TextView>(R.id.tvDistance).text = formatDistance(closest.distanceMeters)
        view.findViewById<TextView>(R.id.tvUserSpeed).text =
            userSpeedKmh?.let { "${it.toInt()} km/h" } ?: "-- km/h"
        view.findViewById<TextView>(R.id.tvLocation).text = buildString {
            if (closest.radar.route.isNotBlank()) append(closest.radar.route)
            if (closest.radar.city.isNotBlank()) {
                if (isNotEmpty()) append(" — ")
                append(closest.radar.city)
            }
        }
        setCountBadge(view, R.id.tvRadarCount, radars.size)
        try { windowManager.updateViewLayout(view, layoutParams) } catch (_: Exception) {}
    }

    private fun setMapAlertState(
        radars: List<ProximityChecker.NearbyRadar>,
        userSpeedKmh: Float?,
        userLat: Double?,
        userLng: Double?,
        bearing: Float?
    ) {
        val view = mapView ?: return
        val map = osmMap ?: return
        val closest = radars.first()

        view.findViewById<TextView>(R.id.mapTvType).text = formatType(closest.radar.type)
        view.findViewById<TextView>(R.id.mapTvSpeed).text = closest.radar.speedLimit?.toString() ?: "?"
        view.findViewById<TextView>(R.id.mapTvDistance).text = formatDistance(closest.distanceMeters)
        view.findViewById<TextView>(R.id.mapTvUserSpeed).text =
            userSpeedKmh?.let { "${it.toInt()} km/h" } ?: "-- km/h"
        setCountBadge(view, R.id.mapTvCount, radars.size)

        updateRadarMarkers(radars, map)

        if (userLat != null && userLng != null) {
            val userGeo = GeoPoint(userLat, userLng)
            userMarker?.position = userGeo
            map.controller.setCenter(userGeo)
            bearing?.let { map.mapOrientation = -it }
        } else {
            map.controller.setCenter(GeoPoint(closest.radar.latitude, closest.radar.longitude))
        }

        map.controller.setZoom(when {
            closest.distanceMeters < 200  -> 17.0
            closest.distanceMeters < 500  -> 16.0
            closest.distanceMeters < 1000 -> 15.0
            else                          -> 14.0
        })

        map.invalidate()
        try { windowManager.updateViewLayout(view, layoutParams) } catch (_: Exception) {}
    }

    // ── Markers radar (liste dynamique) ──────────────────────────────────

    private fun updateRadarMarkers(radars: List<ProximityChecker.NearbyRadar>, map: MapView) {
        radarMarkers.forEach { map.overlays.remove(it) }
        radarMarkers.clear()
        for (nearby in radars) {
            val marker = Marker(map).apply {
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                position = GeoPoint(nearby.radar.latitude, nearby.radar.longitude)
                icon = BitmapDrawable(
                    context.resources,
                    RadarIconFactory.overlayIcon(context, nearby.radar.type, nearby.radar.speedLimit, sizeDp = 32)
                )
            }
            radarMarkers.add(marker)
            map.overlays.add(marker)
        }
        map.invalidate()
    }

    // ── Badge count ───────────────────────────────────────────────────────

    private fun setCountBadge(view: View?, badgeId: Int, count: Int) {
        val badge = view?.findViewById<TextView>(badgeId) ?: return
        if (count > 1) {
            badge.text = "$count radars"
            badge.visibility = View.VISIBLE
        } else {
            badge.visibility = View.GONE
        }
    }

    // ── Dismiss ───────────────────────────────────────────────────────────

    private fun dismissTextView() {
        textView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            textView = null
        }
    }

    private fun dismissMapView() {
        osmMap?.onPause()
        val map = osmMap
        radarMarkers.forEach { map?.overlays?.remove(it) }
        radarMarkers.clear()
        mapView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            mapView = null
        }
        osmMap = null
        userMarker = null
        layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
    }

    private fun dismissCurrentView() {
        dismissTextView()
        dismissMapView()
    }

    // ── Drag ─────────────────────────────────────────────────────────────

    private fun attachDragListener(view: View) {
        var initX = 0; var initY = 0
        var initTouchX = 0f; var initTouchY = 0f
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = layoutParams.x; initY = layoutParams.y
                    initTouchX = event.rawX; initTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initX + (event.rawX - initTouchX).toInt()
                    layoutParams.y = initY + (event.rawY - initTouchY).toInt()
                    val v = textView ?: mapView
                    try { v?.let { windowManager.updateViewLayout(it, layoutParams) } } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    scope.launch(Dispatchers.IO) {
                        settingsRepository.setOverlayPosition(layoutParams.x, layoutParams.y)
                    }
                    true
                }
                else -> false
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun cartoVoyagerSource() = XYTileSource(
        "CartoDB Voyager",
        0, 20, 256, ".png",
        arrayOf(
            "https://a.basemaps.cartocdn.com/rastertiles/voyager/",
            "https://b.basemaps.cartocdn.com/rastertiles/voyager/",
            "https://c.basemaps.cartocdn.com/rastertiles/voyager/",
            "https://d.basemaps.cartocdn.com/rastertiles/voyager/"
        ),
        "© CartoDB © OpenStreetMap contributors"
    )

    private fun formatType(type: String) = when {
        type.contains("feu", ignoreCase = true)          -> "🚦 Feu rouge"
        type.contains("tronçon", ignoreCase = true) ||
        type.contains("troncon", ignoreCase = true)      -> "📏 Tronçon"
        type.contains("discriminant", ignoreCase = true) -> "🚛 Discriminant"
        else                                             -> "📷 Radar fixe"
    }

    private fun formatDistance(meters: Double) = when {
        meters < 1000 -> "${meters.toInt()} m"
        else          -> String.format("%.1f km", meters / 1000)
    }
}
