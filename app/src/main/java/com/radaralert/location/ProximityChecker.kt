package com.radaralert.location

import com.radaralert.data.RadarDao
import com.radaralert.data.RadarEntity
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class ProximityChecker(private val radarDao: RadarDao) {

    companion object {
        private const val GRID_SIZE = 0.05
        private const val EARTH_RADIUS_METERS = 6_371_000.0
    }

    data class NearbyRadar(
        val radar: RadarEntity,
        val distanceMeters: Double
    )

    suspend fun findNearbyRadars(
        userLat: Double,
        userLng: Double,
        alertDistanceMeters: Int
    ): List<NearbyRadar> {
        val userGridLat = floor(userLat / GRID_SIZE).toInt()
        val userGridLng = floor(userLng / GRID_SIZE).toInt()

        // Requête sur la cellule courante + 8 cellules adjacentes (grille 3x3)
        val candidates = radarDao.getRadarsInGridRange(
            minGridLat = userGridLat - 1,
            maxGridLat = userGridLat + 1,
            minGridLng = userGridLng - 1,
            maxGridLng = userGridLng + 1
        )

        return candidates.mapNotNull { radar ->
            val distance = haversineDistance(
                userLat, userLng,
                radar.latitude, radar.longitude
            )
            if (distance <= alertDistanceMeters) {
                NearbyRadar(radar, distance)
            } else null
        }.sortedBy { it.distanceMeters }
    }

    private fun haversineDistance(
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(a))
    }
}
