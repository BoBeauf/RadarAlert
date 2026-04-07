package com.radaralert.location

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.radaralert.MainActivity
import com.radaralert.R
import com.radaralert.alert.AlertManager
import com.radaralert.data.RadarDatabase
import com.radaralert.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var proximityChecker: ProximityChecker
    private lateinit var alertManager: AlertManager
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var locationCallback: LocationCallback

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    companion object {
        private val _locationFlow = MutableStateFlow<Location?>(null)
        val locationFlow: StateFlow<Location?> = _locationFlow

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning

        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "location_service"

        const val ACTION_START = "com.radaralert.START"
        const val ACTION_STOP = "com.radaralert.STOP"
        const val ACTION_TEST = "com.radaralert.TEST"
    }

    override fun onCreate() {
        super.onCreate()
        val db = RadarDatabase.getInstance(applicationContext)
        settingsRepository = SettingsRepository(applicationContext)
        proximityChecker = ProximityChecker(db.radarDao())
        alertManager = AlertManager(applicationContext, settingsRepository, serviceScope)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                _locationFlow.value = location

                val speedKmh = if (location.hasSpeed()) location.speed * 3.6f else null
                val bearing = if (location.hasBearing()) location.bearing else null
                val lat = location.latitude
                val lng = location.longitude
                serviceScope.launch {
                    val alertDistance = settingsRepository.alertDistance.first()
                    val nearby = proximityChecker.findNearbyRadars(lat, lng, alertDistance)
                    alertManager.processNearbyRadars(nearby, speedKmh, lat, lng, bearing)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TEST -> {
                serviceScope.launch { triggerTestAlert() }
                return START_NOT_STICKY
            }
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        startLocationUpdates()
        alertManager.overlay.showIdle()
        _isRunning.value = true
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            5_000L // toutes les 5 secondes
        )
            .setMinUpdateIntervalMillis(3_000L)
            .build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun buildNotification(): Notification {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LocationService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RadarAlert actif")
            .setContentText("Surveillance des radars en cours...")
            .setSmallIcon(R.drawable.ic_radar)
            .setOngoing(true)
            .setContentIntent(mainPendingIntent)
            .addAction(R.drawable.ic_stop, "Arrêter", stopPendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        alertManager.release()
        serviceScope.cancel()
        _isRunning.value = false
        _locationFlow.value = null
    }

    private suspend fun triggerTestAlert() {
        val db = RadarDatabase.getInstance(applicationContext)
        val radar = db.radarDao().getFirstRadar() ?: return
        alertManager.resetCooldowns()
        alertManager.processNearbyRadars(
            listOf(ProximityChecker.NearbyRadar(radar = radar, distanceMeters = 200.0))
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
