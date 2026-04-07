package com.radaralert

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.radaralert.data.CsvParser
import com.radaralert.data.DownloadManager
import com.radaralert.data.RadarDatabase
import com.radaralert.data.SettingsRepository
import com.radaralert.location.LocationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.osmdroid.config.Configuration

class RadarAlertApp : Application() {

    private val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onCreate() {
        super.onCreate()

        MapLibre.getInstance(this)

        Configuration.getInstance().apply {
            load(this@RadarAlertApp, getSharedPreferences("osmdroid", MODE_PRIVATE))
            userAgentValue = "RadarAlert/1.0"
        }

        createNotificationChannel()

        appScope.launch {
            val db = RadarDatabase.getInstance(applicationContext)
            val count = withContext(Dispatchers.IO) { db.radarDao().getCount() }
            if (count == 0) {
                downloadRadarData(db)
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            LocationService.CHANNEL_ID,
            "Surveillance radars",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Service de surveillance des radars en arrière-plan"
            setShowBadge(false)
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private suspend fun downloadRadarData(db: RadarDatabase) {
        try {
            DownloadManager.update(DownloadManager.State.Running("Téléchargement data.gouv.fr…"))
            val radars = withContext(Dispatchers.IO) { CsvParser.downloadAndParse() }
            DownloadManager.update(DownloadManager.State.Running("Insertion de ${radars.size} radars…"))
            withContext(Dispatchers.IO) {
                radars.chunked(500).forEach { db.radarDao().insertAll(it) }
            }
            SettingsRepository(applicationContext).setLastDataUpdate(System.currentTimeMillis())
            DownloadManager.update(DownloadManager.State.Done("${radars.size} radars chargés ✓"))
            delay(3000)
            DownloadManager.update(DownloadManager.State.Idle)
        } catch (e: Exception) {
            DownloadManager.update(DownloadManager.State.Error(e.message ?: "Erreur réseau"))
            delay(5000)
            DownloadManager.update(DownloadManager.State.Idle)
        }
    }
}
