package com.radaralert.alert

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

class SoundAlert {

    fun play() {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            // Bip d'alerte court et distinctif
            toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 600)
            // Libérer après la lecture
            Handler(Looper.getMainLooper()).postDelayed({
                try { toneGen.release() } catch (_: Exception) {}
            }, 800)
        } catch (_: Exception) {
            // ToneGenerator peut échouer sur certains appareils si audio occupé
        }
    }
}
