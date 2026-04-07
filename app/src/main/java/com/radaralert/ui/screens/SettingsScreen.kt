package com.radaralert.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.radaralert.data.CsvParser
import com.radaralert.data.DownloadManager
import com.radaralert.data.OsmParser
import com.radaralert.data.SrParser
import com.radaralert.location.LocationService
import com.radaralert.data.RadarDatabase
import com.radaralert.data.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepo = remember { SettingsRepository(context) }
    val db = remember { RadarDatabase.getInstance(context) }

    val alertDistance by settingsRepo.alertDistance.collectAsStateWithLifecycle(initialValue = 500)
    val soundEnabled by settingsRepo.soundEnabled.collectAsStateWithLifecycle(initialValue = true)
    val lastUpdate by settingsRepo.lastDataUpdate.collectAsStateWithLifecycle(initialValue = 0L)
    val govEnabled by settingsRepo.govEnabled.collectAsStateWithLifecycle(initialValue = true)
    val osmEnabled by settingsRepo.osmEnabled.collectAsStateWithLifecycle(initialValue = false)
    val srEnabled by settingsRepo.srEnabled.collectAsStateWithLifecycle(initialValue = false)

    var isUpdating by remember { mutableStateOf(false) }
    val logs = remember { mutableStateListOf<String>() }
    val dlState by DownloadManager.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paramètres") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Distance d'alerte
            Text(
                text = "Distance d'alerte : $alertDistance m",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "L'alerte se déclenche à cette distance du radar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Slider(
                value = alertDistance.toFloat(),
                onValueChange = { scope.launch { settingsRepo.setAlertDistance(it.toInt()) } },
                valueRange = 200f..1000f,
                steps = 7, // 200, 300, 400, 500, 600, 700, 800, 900, 1000
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("200 m", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.weight(1f))
                Text("1000 m", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(24.dp))
            Divider()
            Spacer(modifier = Modifier.height(24.dp))

            // Son
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Alerte sonore", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Jouer un son à l'approche d'un radar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
                Switch(
                    checked = soundEnabled,
                    onCheckedChange = { scope.launch { settingsRepo.setSoundEnabled(it) } }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Divider()
            Spacer(modifier = Modifier.height(24.dp))

            // Style de la popup
            val overlayStyle by settingsRepo.overlayStyle.collectAsStateWithLifecycle(initialValue = "text")

            Text("Style de la popup d'alerte", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OverlayStyleCard(
                    label = "Texte",
                    description = "Type, vitesse,\ndistance, vitesse GPS",
                    emoji = "📋",
                    selected = overlayStyle == "text",
                    modifier = Modifier.weight(1f),
                    onClick = { scope.launch { settingsRepo.setOverlayStyle("text") } }
                )
                OverlayStyleCard(
                    label = "Carte",
                    description = "Mini carte GPS\nmode navigation",
                    emoji = "🗺️",
                    selected = overlayStyle == "map",
                    modifier = Modifier.weight(1f),
                    onClick = { scope.launch { settingsRepo.setOverlayStyle("map") } }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Divider()
            Spacer(modifier = Modifier.height(24.dp))

            // Sources de données
            Text("Sources de données", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            // Toggle data.gouv.fr
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("data.gouv.fr", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Officiel — Ministère de l'Intérieur (~3 400 radars)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
                Switch(
                    checked = govEnabled,
                    onCheckedChange = { scope.launch { settingsRepo.setGovEnabled(it) } }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Toggle OpenStreetMap
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("OpenStreetMap", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Communautaire — Europe entière (~80 000+ radars)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
                Switch(
                    checked = osmEnabled,
                    onCheckedChange = { scope.launch { settingsRepo.setOsmEnabled(it) } }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Toggle Sécurité Routière
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sécurité Routière (gouv.fr)", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Officiel — superset data.gouv.fr (~3 664 radars)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
                Switch(
                    checked = srEnabled,
                    onCheckedChange = { scope.launch { settingsRepo.setSrEnabled(it) } }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (lastUpdate > 0L) {
                    "Dernière mise à jour : ${
                        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRENCH)
                            .format(Date(lastUpdate))
                    }"
                } else {
                    "Données non téléchargées"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Barre de progression
            if (isUpdating) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (!govEnabled && !osmEnabled && !srEnabled) {
                        logs.clear()
                        logs.add("⚠ Active au moins une source.")
                        return@Button
                    }
                    scope.launch {
                        isUpdating = true
                        logs.clear()

                        // ── Phase 1 : téléchargement (les anciennes données restent en base) ──
                        val downloaded = mutableMapOf<String, List<com.radaralert.data.RadarEntity>>()

                        if (govEnabled) {
                            logs.add("⏳ data.gouv.fr — téléchargement…")
                            DownloadManager.update(DownloadManager.State.Running("data.gouv.fr…"))
                            try {
                                val radars = retryDownload(attempts = 3, delayMs = 3_000,
                                    onRetry = { attempt, err ->
                                        logs.add("  ↺ Tentative $attempt/3 — $err")
                                        DownloadManager.update(DownloadManager.State.Running("data.gouv.fr — tentative $attempt/3…"))
                                    }
                                ) { CsvParser.downloadAndParse() }
                                downloaded["gov"] = radars
                                logs.add("✓ data.gouv.fr — ${radars.size} radars")
                            } catch (e: Exception) {
                                logs.add("✗ data.gouv.fr — ${e.message?.take(80)}")
                                DownloadManager.update(DownloadManager.State.Error("data.gouv.fr échoué"))
                            }
                        }

                        if (srEnabled) {
                            logs.add("⏳ Sécurité Routière — liste en cours…")
                            DownloadManager.update(DownloadManager.State.Running("Sécurité Routière — liste…"))
                            try {
                                val radars = retryDownload(attempts = 2, delayMs = 5_000,
                                    onRetry = { attempt, err ->
                                        logs.add("  ↺ Tentative $attempt/2 — $err")
                                        DownloadManager.update(DownloadManager.State.Running("Sécurité Routière — tentative $attempt/2…"))
                                    }
                                ) {
                                    SrParser.downloadAndParse { done, total ->
                                        DownloadManager.update(DownloadManager.State.Running("Sécurité Routière — $done/$total détails"))
                                    }
                                }
                                downloaded["sr"] = radars
                                logs.add("✓ Sécurité Routière — ${radars.size} radars")
                            } catch (e: Exception) {
                                logs.add("✗ Sécurité Routière — ${e.message?.take(80)}")
                                DownloadManager.update(DownloadManager.State.Error("Sécurité Routière échouée"))
                            }
                        }

                        if (osmEnabled) {
                            logs.add("⏳ OpenStreetMap — téléchargement (peut prendre quelques minutes)…")
                            DownloadManager.update(DownloadManager.State.Running("OpenStreetMap…"))
                            try {
                                val radars = retryDownload(attempts = 3, delayMs = 10_000,
                                    onRetry = { attempt, err ->
                                        logs.add("  ↺ Tentative $attempt/3 — $err")
                                        DownloadManager.update(DownloadManager.State.Running("OpenStreetMap — tentative $attempt/3…"))
                                    }
                                ) { OsmParser.downloadAndParse() }
                                downloaded["osm"] = radars
                                logs.add("✓ OpenStreetMap — ${radars.size} radars")
                            } catch (e: Exception) {
                                logs.add("✗ OpenStreetMap — ${e.message?.take(80)}")
                                DownloadManager.update(DownloadManager.State.Error("OpenStreetMap échoué"))
                            }
                        }

                        // ── Phase 2 : swap atomique — insert new, delete old ──────────────
                        if (downloaded.isNotEmpty()) {
                            logs.add("⏳ Insertion en base de données…")
                            withContext(Dispatchers.IO) {
                                downloaded.forEach { (source, radars) ->
                                    db.radarDao().deleteBySource(source)
                                    radars.chunked(500).forEach { chunk -> db.radarDao().insertAll(chunk) }
                                }
                            }
                            logs.add("✓ Insertion terminée")
                        }

                        // ── Phase 3 : nettoyage des sources désactivées ───────────────────
                        withContext(Dispatchers.IO) {
                            if (!govEnabled) db.radarDao().deleteBySource("gov")
                            if (!srEnabled)  db.radarDao().deleteBySource("sr")
                            if (!osmEnabled) db.radarDao().deleteBySource("osm")
                        }

                        val totalInDb = withContext(Dispatchers.IO) { db.radarDao().getCount() }
                        settingsRepo.setLastDataUpdate(System.currentTimeMillis())
                        val finalMsg = "✓ $totalInDb radars en base"
                        logs.add(finalMsg)
                        DownloadManager.update(DownloadManager.State.Done(finalMsg))
                        isUpdating = false
                    }
                },
                enabled = !isUpdating,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isUpdating) "Mise à jour en cours…" else "Mettre à jour les données radars")
            }

            // ── Journal en direct ──────────────────────────────────────────────
            if (logs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    logs.forEach { line ->
                        val (dot, color) = when {
                            line.startsWith("✓") -> "●" to Color(0xFF4CAF50)
                            line.startsWith("✗") -> "●" to MaterialTheme.colorScheme.error
                            line.startsWith("⚠") -> "●" to Color(0xFFFF9800)
                            line.startsWith("  ↺") -> "↺" to Color(0xFFFF9800)
                            else                -> "○" to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(dot, color = color, fontSize = 10.sp,
                                modifier = Modifier.padding(top = 1.dp))
                            Text(
                                line.trimStart('✓', '✗', '⚠', ' ', '↺'),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = color,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    // Ligne live du DownloadManager pendant l'update
                    if (isUpdating && dlState is DownloadManager.State.Running) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(6.dp).background(
                                MaterialTheme.colorScheme.primary, CircleShape))
                            Text(
                                (dlState as DownloadManager.State.Running).message,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Divider()
            Spacer(modifier = Modifier.height(24.dp))

            // Test alerte
            Text("Test", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Déclenche une fausse alerte pour vérifier le son et l'overlay. Le service doit être actif (bouton ▶ sur la carte).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    val intent = Intent(context, LocationService::class.java).apply {
                        action = LocationService.ACTION_TEST
                    }
                    context.startService(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("🔔 Tester l'alerte radar")
            }

            Spacer(modifier = Modifier.height(24.dp))
            Divider()
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Sources : data.gouv.fr & radars.securite-routiere.gouv.fr — Ministère de l'Intérieur (Licence Ouverte 2.0) · OpenStreetMap contributors (ODbL)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
            )
        }
    }
}

/**
 * Retry helper : tente [attempts] fois avec [delayMs] * tentative ms entre chaque essai.
 * [onRetry] est appelé sur le thread principal avant chaque ré-essai.
 */
private suspend fun <T> retryDownload(
    attempts: Int = 3,
    delayMs: Long = 3_000,
    onRetry: ((attempt: Int, error: String) -> Unit)? = null,
    block: suspend () -> T
): T {
    var lastException: Exception? = null
    for (attempt in 0 until attempts) {
        if (attempt > 0) {
            onRetry?.invoke(attempt + 1, lastException?.message?.take(60) ?: "erreur réseau")
            delay(delayMs * attempt) // backoff linéaire : 0, 1x, 2x
        }
        try {
            return block()
        } catch (e: Exception) {
            lastException = e
        }
    }
    throw lastException!!
}

@Composable
private fun OverlayStyleCard(
    label: String,
    description: String,
    emoji: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
    val bgColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent
    Column(
        modifier = modifier
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .background(bgColor, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(2.dp))
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
