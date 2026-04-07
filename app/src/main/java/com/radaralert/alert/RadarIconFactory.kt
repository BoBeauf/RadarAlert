package com.radaralert.alert

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

object RadarIconFactory {

    fun iconKey(type: String, speed: Int?): String = when {
        type.contains("feu", ignoreCase = true) -> "icon_tl"
        speed != null && speed > 0 -> "icon_s_$speed"
        else -> "icon_s_0"
    }

    /** Génère tous les icônes pour la carte principale MapLibre. */
    fun allIcons(context: Context, sizeDp: Int = 40): Map<String, Bitmap> {
        val px = dpToPx(context, sizeDp)
        val result = mutableMapOf<String, Bitmap>()
        listOf(20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 0).forEach { speed ->
            result["icon_s_$speed"] = speedSignBitmap(speed, px)
        }
        result["icon_tl"] = trafficLightBitmap(px)
        return result
    }

    /** Icône dynamique pour la mini-carte overlay osmdroid. */
    fun overlayIcon(context: Context, type: String, speed: Int?, sizeDp: Int = 30): Bitmap {
        val px = dpToPx(context, sizeDp)
        return if (type.contains("feu", ignoreCase = true)) {
            trafficLightBitmap(px)
        } else {
            speedSignBitmap(speed ?: 0, px)
        }
    }

    private fun dpToPx(context: Context, dp: Int): Int =
        (dp * context.resources.displayMetrics.density).toInt()

    private fun speedSignBitmap(speedKmh: Int, sizePx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = sizePx / 2f
        val cy = sizePx / 2f
        val margin = sizePx * 0.05f
        val outerR = cx - margin

        // Ombre
        paint.color = android.graphics.Color.argb(40, 0, 0, 0)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy + sizePx * 0.05f, outerR * 0.95f, paint)

        // Fond blanc
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(cx, cy, outerR, paint)

        // Bordure rouge épaisse
        paint.color = android.graphics.Color.argb(255, 211, 47, 47)
        paint.style = Paint.Style.STROKE
        val borderWidth = sizePx * 0.14f
        paint.strokeWidth = borderWidth
        canvas.drawCircle(cx, cy, outerR - borderWidth / 2, paint)

        // Texte vitesse
        paint.style = Paint.Style.FILL
        paint.color = android.graphics.Color.argb(255, 17, 17, 17)
        paint.typeface = Typeface.DEFAULT_BOLD
        val text = if (speedKmh > 0) speedKmh.toString() else "?"
        paint.textSize = when {
            text.length >= 3 -> sizePx * 0.29f
            else             -> sizePx * 0.36f
        }
        paint.textAlign = Paint.Align.CENTER
        val fm = paint.fontMetrics
        canvas.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2f, paint)

        return bmp
    }

    private fun trafficLightBitmap(sizePx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val w   = sizePx * 0.52f
        val h   = sizePx * 0.88f
        val l   = (sizePx - w) / 2f
        val t   = (sizePx - h) / 2f
        val r   = sizePx * 0.08f

        // Ombre
        paint.color = android.graphics.Color.argb(45, 0, 0, 0)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(l + sizePx*0.04f, t + sizePx*0.04f, l+w + sizePx*0.04f, t+h + sizePx*0.04f), r, r, paint)

        // Boîtier noir
        paint.color = android.graphics.Color.argb(255, 30, 30, 30)
        canvas.drawRoundRect(RectF(l, t, l + w, t + h), r, r, paint)

        // Contour gris
        paint.color = android.graphics.Color.argb(255, 66, 66, 66)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = sizePx * 0.025f
        canvas.drawRoundRect(RectF(l, t, l + w, t + h), r, r, paint)
        paint.style = Paint.Style.FILL

        val cx    = sizePx / 2f
        val lightR = w * 0.27f
        val step  = h / 4f

        // Rouge (allumé)
        paint.color = android.graphics.Color.argb(255, 255, 23, 68)
        canvas.drawCircle(cx, t + step, lightR, paint)
        // Reflet rouge
        paint.color = android.graphics.Color.argb(100, 255, 255, 255)
        canvas.drawCircle(cx - lightR * 0.2f, t + step - lightR * 0.3f, lightR * 0.32f, paint)

        // Ambre (éteint)
        paint.color = android.graphics.Color.argb(100, 255, 160, 0)
        canvas.drawCircle(cx, t + step * 2, lightR, paint)

        // Vert (éteint)
        paint.color = android.graphics.Color.argb(90, 0, 200, 83)
        canvas.drawCircle(cx, t + step * 3, lightR, paint)

        return bmp
    }
}
