package com.radaralert.alert

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.radaralert.R
import com.radaralert.data.RadarEntity

class OverlayManager(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null

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

    fun show(radar: RadarEntity, distanceMeters: Double, userSpeedKmh: Float?) {
        if (!Settings.canDrawOverlays(context)) {
            Log.w("OverlayManager", "Permission SYSTEM_ALERT_WINDOW non accordée — overlay ignoré")
            return
        }
        mainHandler.post {
            if (overlayView == null) {
                val view = LayoutInflater.from(context).inflate(R.layout.overlay_alert, null)
                attachDragListener(view)
                overlayView = view
                try {
                    windowManager.addView(view, layoutParams)
                } catch (e: Exception) {
                    Log.e("OverlayManager", "addView failed: ${e.message}")
                    overlayView = null
                    return@post
                }
            }
            bind(radar, distanceMeters, userSpeedKmh)
        }
    }

    fun update(distanceMeters: Double, userSpeedKmh: Float?) {
        mainHandler.post {
            val view = overlayView ?: return@post
            view.findViewById<TextView>(R.id.tvDistance).text = formatDistance(distanceMeters)
            userSpeedKmh?.let {
                view.findViewById<TextView>(R.id.tvUserSpeed).text = "${it.toInt()} km/h"
            }
        }
    }

    fun dismiss() {
        mainHandler.post {
            overlayView?.let { view ->
                try { windowManager.removeView(view) } catch (_: Exception) {}
                overlayView = null
            }
        }
    }

    private fun attachDragListener(view: View) {
        var initX = 0; var initY = 0
        var initTouchX = 0f; var initTouchY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = layoutParams.x
                    initY = layoutParams.y
                    initTouchX = event.rawX
                    initTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initX + (event.rawX - initTouchX).toInt()
                    layoutParams.y = initY + (event.rawY - initTouchY).toInt()
                    try { windowManager.updateViewLayout(view, layoutParams) } catch (_: Exception) {}
                    true
                }
                else -> false
            }
        }
    }

    private fun bind(radar: RadarEntity, distanceMeters: Double, userSpeedKmh: Float?) {
        val view = overlayView ?: return
        view.findViewById<TextView>(R.id.tvType).text = formatType(radar.type)
        view.findViewById<TextView>(R.id.tvSpeed).text = radar.speedLimit?.toString() ?: "?"
        view.findViewById<TextView>(R.id.tvDistance).text = formatDistance(distanceMeters)
        view.findViewById<TextView>(R.id.tvUserSpeed).text =
            userSpeedKmh?.let { "${it.toInt()} km/h" } ?: "-- km/h"
        view.findViewById<TextView>(R.id.tvLocation).text = buildString {
            if (radar.route.isNotBlank()) append(radar.route)
            if (radar.city.isNotBlank()) {
                if (isNotEmpty()) append(" — ")
                append(radar.city)
            }
        }
    }

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
