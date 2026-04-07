package com.radaralert.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.RectF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.google.android.gms.location.LocationServices
import com.radaralert.alert.RadarIconFactory
import com.radaralert.data.DownloadManager
import com.radaralert.data.RadarDatabase
import com.radaralert.data.RadarEntity
import com.radaralert.data.SettingsRepository
import com.radaralert.location.LocationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import kotlin.math.floor

private const val STYLE_URL   = "https://tiles.openfreemap.org/styles/liberty"
private const val SOURCE_ID   = "radars-source"
private const val LAYER_GLOW  = "radars-glow"
private const val LAYER_DOTS  = "radars-dots"
private const val LAYER_ICONS = "radars-icons"

data class RadarClickInfo(
    val type: String, val source: String,
    val speed: Int?, val speedHgv: Int?,
    val city: String, val route: String, val direction: String,
    val department: String, val latitude: Double, val longitude: Double,
    val equipment: String, val installDate: String, val sectionLengthKm: String
)

@Composable
fun MapScreen(navController: NavController) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val location  by LocationService.locationFlow.collectAsStateWithLifecycle()
    val isRunning by LocationService.isRunning.collectAsStateWithLifecycle()
    val dlState   by DownloadManager.state.collectAsStateWithLifecycle()

    val db           = remember { RadarDatabase.getInstance(context) }
    val settingsRepo = remember { SettingsRepository(context) }
    val scope        = rememberCoroutineScope()
    val soundEnabled by settingsRepo.soundEnabled.collectAsStateWithLifecycle(false)

    var nearbyRadars   by remember { mutableStateOf<List<RadarEntity>>(emptyList()) }
    var radarCount     by remember { mutableStateOf(0) }
    var mapLibreMap    by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady     by remember { mutableStateOf(false) }
    var centeredOnUser  by remember { mutableStateOf(false) }
    var selectedRadars  by remember { mutableStateOf<List<RadarClickInfo>>(emptyList()) }
    var selectedIndex   by remember { mutableStateOf(0) }

    // ── Chargement initial — Europe entière ──────────────────────────────
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            radarCount = db.radarDao().getCount()
            if (radarCount > 0) {
                nearbyRadars = db.radarDao().getRadarsInGridRange(
                    minGridLat = 700,  maxGridLat = 1440,
                    minGridLng = -220, maxGridLng = 900
                )
            }
        }
    }

    // ── Reload après téléchargement ──────────────────────────────────────
    LaunchedEffect(dlState) {
        if (dlState is DownloadManager.State.Done || dlState is DownloadManager.State.Idle) {
            withContext(Dispatchers.IO) {
                val newCount = db.radarDao().getCount()
                if (newCount != radarCount) {
                    radarCount = newCount
                    nearbyRadars = db.radarDao().getRadarsInGridRange(
                        minGridLat = 700, maxGridLat = 1440,
                        minGridLng = -220, maxGridLng = 900
                    )
                }
            }
        }
    }

    // ── Update GPS ───────────────────────────────────────────────────────
    LaunchedEffect(location) {
        location?.let { loc ->
            withContext(Dispatchers.IO) {
                val gl = floor(loc.latitude  / 0.05).toInt()
                val gn = floor(loc.longitude / 0.05).toInt()
                nearbyRadars = db.radarDao().getRadarsInGridRange(gl - 5, gl + 5, gn - 5, gn + 5)
                radarCount   = db.radarDao().getCount()
            }
            if (!centeredOnUser) {
                mapLibreMap?.animateCamera(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                            .target(LatLng(loc.latitude, loc.longitude))
                            .zoom(14.0).build()
                    )
                )
                centeredOnUser = true
            }
        }
    }

    // ── GeoJSON ──────────────────────────────────────────────────────────
    LaunchedEffect(nearbyRadars, styleReady) {
        if (!styleReady) return@LaunchedEffect
        mapLibreMap?.style?.let { style ->
            (style.getSource(SOURCE_ID) as? GeoJsonSource)
                ?.setGeoJson(buildRadarGeoJson(nearbyRadars))
        }
    }

    // ── MapView lifecycle ────────────────────────────────────────────────
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    DisposableEffect(lifecycleOwner) {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))  mapView.onStart()
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START   -> mapView.onStart()
                Lifecycle.Event.ON_RESUME  -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE   -> mapView.onPause()
                Lifecycle.Event.ON_STOP    -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // ── UI ────────────────────────────────────────────────────────────────
    Column(Modifier.fillMaxSize()) {

        MapHeader(
            radarCount   = radarCount,
            soundEnabled = soundEnabled,
            dlState      = dlState,
            onSoundToggle = { scope.launch { settingsRepo.setSoundEnabled(!soundEnabled) } },
            onSettings   = { navController.navigate("settings") }
        )

        Box(Modifier.weight(1f)) {

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { _ ->
                        mapView.also { mv ->
                            mv.getMapAsync { map ->
                                mapLibreMap = map
                                setupMapLibre(context, map,
                                    onRadarsClick = { infos ->
                                        selectedRadars = infos
                                        selectedIndex  = 0
                                    },
                                    onMapClick = { selectedRadars = emptyList() }
                                ) { styleReady = true }
                            }
                        }
                    }
                )

                // FABs bas droite
                Column(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    FloatingActionButton(
                        onClick = {
                            locateUser(
                                context,
                                location?.let {
                                    android.location.Location("").apply {
                                        latitude  = it.latitude
                                        longitude = it.longitude
                                    }
                                },
                                mapLibreMap
                            )
                        },
                        modifier = Modifier.size(48.dp),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Icon(Icons.Default.GpsFixed, null)
                    }
                    Spacer(Modifier.height(8.dp))
                    FloatingActionButton(
                        onClick = {
                            val intent = Intent(context, LocationService::class.java).apply {
                                action = if (isRunning) LocationService.ACTION_STOP
                                         else LocationService.ACTION_START
                            }
                            if (isRunning) context.startService(intent)
                            else context.startForegroundService(intent)
                        },
                        containerColor = if (isRunning) MaterialTheme.colorScheme.error
                                         else MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            null,
                            tint = androidx.compose.ui.graphics.Color.White
                        )
                    }
                }

                // Badge statut
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                        .background(androidx.compose.ui.graphics.Color(0xCC1A1A2E), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isRunning) "● Surveillance active" else "○ En pause",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isRunning) androidx.compose.ui.graphics.Color(0xFF4CAF50)
                                else androidx.compose.ui.graphics.Color.Gray
                    )
                }

                RadarCardAnimated(
                    radars       = selectedRadars,
                    currentIndex = selectedIndex,
                    onIndexChange = { selectedIndex = it },
                    modifier     = Modifier.align(Alignment.BottomCenter),
                    onDismiss    = { selectedRadars = emptyList(); selectedIndex = 0 }
                )
            }
        }
}

// ── Header ─────────────────────────────────────────────────────────────────

@Composable
private fun MapHeader(
    radarCount: Int,
    soundEnabled: Boolean,
    dlState: DownloadManager.State,
    onSoundToggle: () -> Unit,
    onSettings: () -> Unit
) {
    val isDownloading = dlState is DownloadManager.State.Running
    val dlMessage = when (dlState) {
        is DownloadManager.State.Running -> dlState.message
        is DownloadManager.State.Done    -> "✓ ${dlState.message}"
        is DownloadManager.State.Error   -> "✗ ${dlState.message}"
        else -> null
    }
    val dlColor = when (dlState) {
        is DownloadManager.State.Done  -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
        is DownloadManager.State.Error -> androidx.compose.ui.graphics.Color(0xFFFF5252)
        else -> androidx.compose.ui.graphics.Color(0xFF42A5F5)
    }

    Column(
        modifier = Modifier.fillMaxWidth().background(
            Brush.verticalGradient(
                listOf(
                    androidx.compose.ui.graphics.Color(0xFF16213E),
                    androidx.compose.ui.graphics.Color(0xFF0F3460)
                )
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Logo panneau vitesse
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(androidx.compose.ui.graphics.Color.White, CircleShape)
                    .border(4.dp, androidx.compose.ui.graphics.Color(0xFFD32F2F), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "R",
                    color = androidx.compose.ui.graphics.Color(0xFF111111),
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    lineHeight = 20.sp
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    "RadarAlert",
                    color = androidx.compose.ui.graphics.Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    lineHeight = 20.sp
                )
                if (radarCount > 0) {
                    val formatted = "%,d".format(radarCount).replace(',', '\u00A0')
                    Text(
                        "$formatted radars",
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(
                        "Aucune donnée — Paramètres",
                        color = androidx.compose.ui.graphics.Color(0xFFFF5252),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            IconButton(onClick = onSoundToggle) {
                Icon(
                    if (soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    null,
                    tint = if (soundEnabled) androidx.compose.ui.graphics.Color.White
                           else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(onClick = onSettings) {
                Icon(
                    Icons.Default.Settings, null,
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Barre progression
        AnimatedVisibility(
            visible = dlMessage != null,
            enter = fadeIn() + expandVertically(),
            exit  = fadeOut() + shrinkVertically()
        ) {
            Column {
                if (isDownloading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = androidx.compose.ui.graphics.Color(0xFF1E88E5),
                        trackColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.1f)
                    )
                }
                dlMessage?.let { msg ->
                    Text(
                        text = msg,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = dlColor
                    )
                }
            }
        }
    }
}

// ── Animated wrapper (avoids ColumnScope.AnimatedVisibility resolution) ───────

@Composable
private fun RadarCardAnimated(
    radars: List<RadarClickInfo>,
    currentIndex: Int,
    onIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = radars.isNotEmpty(),
        modifier = modifier,
        enter = slideInVertically { it },
        exit  = slideOutVertically { it }
    ) {
        val radar = radars.getOrNull(currentIndex)
        if (radar != null) {
            RadarInfoCard(
                info         = radar,
                currentIndex = currentIndex,
                totalCount   = radars.size,
                onPrev       = { if (currentIndex > 0) onIndexChange(currentIndex - 1) },
                onNext       = { if (currentIndex < radars.lastIndex) onIndexChange(currentIndex + 1) },
                onDismiss    = onDismiss
            )
        }
    }
}

// ── Fiche radar ───────────────────────────────────────────────────────────────

@Composable
private fun RadarInfoCard(
    info: RadarClickInfo,
    currentIndex: Int,
    totalCount: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit
) {
    val sourceColor = when (info.source) {
        "osm" -> androidx.compose.ui.graphics.Color(0xFF1E88E5)
        "sr"  -> androidx.compose.ui.graphics.Color(0xFFFF8F00)
        else  -> androidx.compose.ui.graphics.Color(0xFFE53935)
    }
    val sourceLabel = when (info.source) {
        "osm" -> "OpenStreetMap"
        "sr"  -> "Sécurité Routière"
        else  -> "data.gouv.fr"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {

            // Handle bar
            Box(
                Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 0.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .width(36.dp).height(4.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            RoundedCornerShape(2.dp)
                        )
                )
            }

            // Titre + bouton fermer
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    formatRadarType(info.type),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.Close, null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Badges vitesse VL + PL
            if (info.speed != null || info.speedHgv != null) {
                Row(
                    Modifier.padding(start = 20.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (info.speed != null) {
                        Box(
                            Modifier
                                .background(androidx.compose.ui.graphics.Color(0xFFD32F2F), RoundedCornerShape(100.dp))
                                .padding(horizontal = 20.dp, vertical = 9.dp)
                        ) {
                            Text(
                                "${info.speed} km/h",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                                color = androidx.compose.ui.graphics.Color.White
                            )
                        }
                    }
                    if (info.speedHgv != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier
                                    .background(androidx.compose.ui.graphics.Color(0xFF5D4037), RoundedCornerShape(100.dp))
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    "${info.speedHgv} km/h",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = androidx.compose.ui.graphics.Color.White
                                )
                            }
                            Text(
                                "Poids lourds",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
            }

            // Bloc infos
            val rows = buildList {
                if (info.route.isNotBlank())           add("Route"         to info.route)
                if (info.city.isNotBlank())            add("Emplacement"   to info.city)
                if (info.direction.isNotBlank())       add("Direction"     to info.direction)
                if (info.department.isNotBlank())      add("Département"   to info.department)
                if (info.sectionLengthKm.isNotBlank()) add("Longueur"      to "${info.sectionLengthKm} km")
                if (info.equipment.isNotBlank())       add("Équipement"    to info.equipment)
                if (info.installDate.isNotBlank())     add("Installation"  to formatDate(info.installDate))
                if (info.latitude != 0.0)              add("Coordonnées"   to "%.5f, %.5f".format(info.latitude, info.longitude))
            }

            if (rows.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.045f),
                            RoundedCornerShape(14.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    rows.forEachIndexed { idx, (label, value) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
                                modifier = Modifier.widthIn(min = 82.dp)
                            )
                            Text(
                                value,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (idx < rows.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
            }

            // Source chip + navigation
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .background(sourceColor.copy(alpha = 0.1f), RoundedCornerShape(100.dp))
                        .border(1.dp, sourceColor.copy(alpha = 0.22f), RoundedCornerShape(100.dp))
                ) {
                    if (totalCount > 1) {
                        // Mode navigation
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onPrev,
                                modifier = Modifier.size(34.dp),
                                enabled = currentIndex > 0
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowLeft, null,
                                    tint = if (currentIndex > 0) sourceColor
                                           else sourceColor.copy(alpha = 0.25f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Text(
                                    sourceLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = sourceColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "${currentIndex + 1} / $totalCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = sourceColor.copy(alpha = 0.6f)
                                )
                            }
                            IconButton(
                                onClick = onNext,
                                modifier = Modifier.size(34.dp),
                                enabled = currentIndex < totalCount - 1
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowRight, null,
                                    tint = if (currentIndex < totalCount - 1) sourceColor
                                           else sourceColor.copy(alpha = 0.25f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else {
                        // Chip simple
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Box(Modifier.size(7.dp).background(sourceColor, CircleShape))
                            Text(
                                sourceLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = sourceColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "2003-11-05T00:00:00Z" → "05/11/2003" */
private fun formatDate(raw: String): String {
    return try {
        val d = raw.substring(0, 10).split("-")
        "${d[2]}/${d[1]}/${d[0]}"
    } catch (_: Exception) { raw }
}

private fun formatRadarType(type: String) = when {
    type.contains("feu",          ignoreCase = true) -> "Feu rouge"
    type.contains("tronçon",      ignoreCase = true) ||
    type.contains("troncon",      ignoreCase = true) -> "Tronçon"
    type.contains("discriminant", ignoreCase = true) -> "Discriminant"
    type.contains("itinéraire",   ignoreCase = true) -> "Itinéraire"
    type.contains("niveau",       ignoreCase = true) -> "Passage à niveau"
    type.contains("urbain",       ignoreCase = true) -> "Urbain"
    else                                             -> "Radar fixe"
}

// ── MapLibre ──────────────────────────────────────────────────────────────────

@SuppressLint("MissingPermission")
private fun setupMapLibre(
    context: android.content.Context,
    map: MapLibreMap,
    onRadarsClick: (List<RadarClickInfo>) -> Unit,
    onMapClick: () -> Unit,
    onStyleReady: () -> Unit
) {
    map.uiSettings.apply {
        isCompassEnabled     = true
        isAttributionEnabled = false
        isLogoEnabled        = false
    }

    map.setStyle(Style.Builder().fromUri(STYLE_URL)) { style ->

        RadarIconFactory.allIcons(context, sizeDp = 40).forEach { (key, bmp) ->
            style.addImage(key, bmp)
        }

        style.addSource(GeoJsonSource(SOURCE_ID, """{"type":"FeatureCollection","features":[]}"""))

        style.addLayer(CircleLayer(LAYER_GLOW, SOURCE_ID).withProperties(
            PropertyFactory.circleColor(
                Expression.match(Expression.get("source"),
                    Expression.literal("osm"), Expression.color(Color.parseColor("#1565C0")),
                    Expression.literal("sr"),  Expression.color(Color.parseColor("#E65100")),
                    Expression.color(Color.parseColor("#B71C1C")))
            ),
            PropertyFactory.circleRadius(16f),
            PropertyFactory.circleBlur(1.4f),
            PropertyFactory.circleOpacity(0.20f)
        ))

        style.addLayer(CircleLayer(LAYER_DOTS, SOURCE_ID).withProperties(
            PropertyFactory.circleColor(
                Expression.match(Expression.get("source"),
                    Expression.literal("osm"), Expression.color(Color.parseColor("#1E88E5")),
                    Expression.literal("sr"),  Expression.color(Color.parseColor("#FF8F00")),
                    Expression.color(Color.parseColor("#E53935")))
            ),
            PropertyFactory.circleRadius(5f),
            PropertyFactory.circleStrokeWidth(1.2f),
            PropertyFactory.circleStrokeColor(Expression.color(Color.WHITE)),
            PropertyFactory.circleOpacity(0.9f)
        ).also { it.maxZoom = 11f })

        style.addLayer(
            SymbolLayer(LAYER_ICONS, SOURCE_ID).withProperties(
                PropertyFactory.iconImage(Expression.get("iconKey")),
                PropertyFactory.iconSize(
                    Expression.interpolate(
                        Expression.linear(), Expression.zoom(),
                        Expression.stop(11.0, 0.45f),
                        Expression.stop(14.0, 0.72f),
                        Expression.stop(17.0, 1.0f)
                    )
                ),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true)
            ).also { it.minZoom = 11f }
        )

        try {
            val opts = LocationComponentOptions.builder(context)
                .pulseEnabled(true).pulseColor(Color.parseColor("#1565C0"))
                .pulseFadeEnabled(true).foregroundTintColor(Color.parseColor("#1E88E5"))
                .accuracyColor(Color.parseColor("#1E88E5")).accuracyAlpha(0.12f).build()
            val lc = map.locationComponent
            lc.activateLocationComponent(
                LocationComponentActivationOptions.builder(context, style)
                    .locationComponentOptions(opts).build()
            )
            lc.isLocationComponentEnabled = true
            lc.cameraMode  = CameraMode.NONE
            lc.renderMode  = RenderMode.COMPASS
        } catch (_: Exception) {}

        map.cameraPosition = CameraPosition.Builder().target(LatLng(46.6, 2.3)).zoom(6.0).build()
        onStyleReady()
    }

    map.addOnMapClickListener { latLng ->
        val pt   = map.projection.toScreenLocation(latLng)
        val rect = RectF(pt.x - 28f, pt.y - 28f, pt.x + 28f, pt.y + 28f)
        val features = map.queryRenderedFeatures(rect, LAYER_ICONS, LAYER_DOTS)
        if (features.isNotEmpty()) {
            val infos = features.mapNotNull { feature ->
                feature.properties()?.let { props ->
                    RadarClickInfo(
                        type            = props.get("radarType")?.asString      ?: "",
                        source          = props.get("source")?.asString         ?: "",
                        speed           = props.get("speed")?.takeIf    { !it.isJsonNull }?.asInt,
                        speedHgv        = props.get("speedHgv")?.takeIf { !it.isJsonNull }?.asInt,
                        city            = props.get("city")?.asString           ?: "",
                        route           = props.get("route")?.asString          ?: "",
                        direction       = props.get("direction")?.asString      ?: "",
                        department      = props.get("department")?.asString     ?: "",
                        latitude        = props.get("lat")?.takeIf { !it.isJsonNull }?.asDouble ?: 0.0,
                        longitude       = props.get("lng")?.takeIf { !it.isJsonNull }?.asDouble ?: 0.0,
                        equipment       = props.get("equipment")?.asString      ?: "",
                        installDate     = props.get("installDate")?.asString    ?: "",
                        sectionLengthKm = props.get("sectionLengthKm")?.asString ?: ""
                    )
                }
            }.distinctBy { "${it.source}_${it.latitude}_${it.longitude}" }
            if (infos.isNotEmpty()) {
                onRadarsClick(infos)
                return@addOnMapClickListener true
            }
        }
        onMapClick()
        false
    }
}

// ── Localiser ─────────────────────────────────────────────────────────────────

@SuppressLint("MissingPermission")
private fun locateUser(context: Context, location: android.location.Location?, map: MapLibreMap?) {
    fun go(lat: Double, lng: Double) = map?.animateCamera(
        CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder().target(LatLng(lat, lng)).zoom(15.0).build()
        )
    )
    if (location != null) {
        go(location.latitude, location.longitude)
    } else {
        LocationServices.getFusedLocationProviderClient(context)
            .lastLocation.addOnSuccessListener { loc -> loc?.let { go(it.latitude, it.longitude) } }
    }
}

// ── GeoJSON ───────────────────────────────────────────────────────────────────

private fun buildRadarGeoJson(radars: List<RadarEntity>): String = buildString {
    append("""{"type":"FeatureCollection","features":[""")
    var first = true
    for (r in radars) {
        if (!first) append(",")
        first = false
        val speed      = if ((r.speedLimit    ?: 0) > 0) r.speedLimit.toString()    else "null"
        val speedHgv   = if ((r.speedLimitHgv ?: 0) > 0) r.speedLimitHgv.toString() else "null"
        val typeEsc    = r.type.replace("\"", "\\\"")
        val cityEsc    = r.city.replace("\"", "\\\"")
        val routeEsc   = r.route.replace("\"", "\\\"")
        val dirEsc     = r.direction.replace("\"", "\\\"")
        val deptEsc    = r.department.replace("\"", "\\\"")
        val equipEsc   = r.equipment.replace("\"", "\\\"")
        val dateEsc    = r.installDate.replace("\"", "\\\"")
        val sectionEsc = r.sectionLengthKm.replace("\"", "\\\"")
        val iconKey    = RadarIconFactory.iconKey(r.type, r.speedLimit)
        append("""{"type":"Feature","geometry":{"type":"Point","coordinates":[${r.longitude},${r.latitude}]},"properties":{"radarType":"$typeEsc","source":"${r.source}","speed":$speed,"speedHgv":$speedHgv,"city":"$cityEsc","route":"$routeEsc","direction":"$dirEsc","department":"$deptEsc","equipment":"$equipEsc","installDate":"$dateEsc","sectionLengthKm":"$sectionEsc","lat":${r.latitude},"lng":${r.longitude},"iconKey":"$iconKey"}}""")
    }
    append("]}")
}
