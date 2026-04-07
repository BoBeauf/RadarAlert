package com.radaralert.alert

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class VoiceAlert(context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.FRENCH)
                isReady = result != TextToSpeech.LANG_MISSING_DATA &&
                          result != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
    }

    fun speak(radarType: String, speedLimit: Int?, count: Int = 1) {
        if (!isReady) return

        val speedText = speedLimit?.let { ", $it kilomètres heure" } ?: ""

        val message = if (count >= 2) {
            val n = when (count) {
                2 -> "Deux"; 3 -> "Trois"; 4 -> "Quatre"; 5 -> "Cinq"
                else -> "$count"
            }
            "$n radars$speedText."
        } else {
            val typeText = when {
                radarType.contains("feu", ignoreCase = true)         -> "Radar feu rouge"
                radarType.contains("tronçon", ignoreCase = true) ||
                radarType.contains("troncon", ignoreCase = true)     -> "Radar tronçon"
                radarType.contains("discriminant", ignoreCase = true) -> "Radar discriminant"
                else                                                  -> "Radar fixe"
            }
            "$typeText$speedText."
        }

        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "radar_alert")
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
