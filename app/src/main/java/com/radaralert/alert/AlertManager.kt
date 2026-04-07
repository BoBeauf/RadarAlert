package com.radaralert.alert

import android.content.Context
import com.radaralert.data.SettingsRepository
import com.radaralert.location.ProximityChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first

class AlertManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope
) {
    private val voiceAlert = VoiceAlert(context)
    val overlay = RadarOverlayManager(context, settingsRepository, scope)

    private val alertCooldowns = mutableMapOf<Long, Long>()
    private val COOLDOWN_MS = 2 * 60 * 1000L
    private var activeRadarIds: Set<Long> = emptySet()

    suspend fun processNearbyRadars(
        nearbyRadars: List<ProximityChecker.NearbyRadar>,
        userSpeedKmh: Float? = null,
        userLat: Double? = null,
        userLng: Double? = null,
        bearing: Float? = null
    ) {
        val soundEnabled = settingsRepository.soundEnabled.first()
        val now = System.currentTimeMillis()
        alertCooldowns.entries.removeIf { now - it.value > COOLDOWN_MS }

        if (nearbyRadars.isEmpty()) {
            if (activeRadarIds.isNotEmpty()) {
                overlay.backToIdle()
                activeRadarIds = emptySet()
            }
            return
        }

        val closest = nearbyRadars.first()
        val newIds = nearbyRadars.map { it.radar.id }.toSet()

        // Radars qui viennent d'entrer dans la zone
        val newEntries = newIds - activeRadarIds

        if (newEntries.isNotEmpty()) {
            // Annoncer si au moins un nouveau radar n'est pas en cooldown
            val uncooled = newEntries.filter { !alertCooldowns.containsKey(it) }
            if (soundEnabled && uncooled.isNotEmpty()) {
                voiceAlert.speak(closest.radar.type, closest.radar.speedLimit, nearbyRadars.size)
            }
            newEntries.forEach { alertCooldowns[it] = now }
            activeRadarIds = newIds
            overlay.showAlert(nearbyRadars, userSpeedKmh, userLat, userLng, bearing)
        } else {
            activeRadarIds = newIds
            overlay.updateAlert(nearbyRadars, userSpeedKmh, userLat, userLng, bearing)
        }
    }

    fun resetCooldowns() {
        alertCooldowns.clear()
        activeRadarIds = emptySet()
    }

    fun release() {
        voiceAlert.release()
        overlay.dismiss()
    }
}
