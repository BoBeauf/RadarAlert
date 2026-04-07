package com.radaralert.alert

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.radaralert.R

class StatusOverlayManager(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var statusView: View? = null

    private val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.END
        x = 12
        y = 72
    }

    fun show() {
        if (!Settings.canDrawOverlays(context)) return
        mainHandler.post {
            if (statusView != null) return@post
            val view = LayoutInflater.from(context).inflate(R.layout.overlay_status, null)
            attachDragListener(view)
            statusView = view
            try {
                windowManager.addView(view, layoutParams)
            } catch (e: Exception) {
                statusView = null
            }
        }
    }

    fun dismiss() {
        mainHandler.post {
            statusView?.let { view ->
                try { windowManager.removeView(view) } catch (_: Exception) {}
                statusView = null
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
}
