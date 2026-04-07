package com.radaralert.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.floor

object SrParser {

    private const val SR_BASE        = "https://radars.securite-routiere.gouv.fr"
    private const val GRID_SIZE      = 0.05
    private const val MAX_CONCURRENT = 5   // serveur fragile : dépasser 10 provoque des coupures
    private const val DETAIL_RETRIES = 3   // tentatives par requête détail

    /**
     * Télécharge la liste complète puis enrichit chaque radar
     * avec ses détails (département, route, direction, équipement,
     * date d'installation, longueur tronçon, vitesses VL/PL).
     *
     * @param onProgress callback (done, total) — appelable depuis Dispatchers.IO
     */
    suspend fun downloadAndParse(
        onProgress: ((done: Int, total: Int) -> Unit)? = null
    ): List<RadarEntity> = withContext(Dispatchers.IO) {

        val dispatcher = Dispatcher().apply {
            maxRequestsPerHost = MAX_CONCURRENT   // aligne OkHttp avec notre sémaphore
            maxRequests        = MAX_CONCURRENT
        }
        val client = OkHttpClient.Builder()
            .followRedirects(true)
            .retryOnConnectionFailure(false)            // on gère nous-mêmes les retries
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .connectionPool(                            // courte durée pour éviter les connexions "stale"
                ConnectionPool(MAX_CONCURRENT, 10, TimeUnit.SECONDS)
            )
            .dispatcher(dispatcher)
            .build()

        // ── Étape 1 : liste de base (/radars/all) ──────────────────────────
        val basicList = fetchAllBasic(client)
        val total     = basicList.size
        val doneCount = AtomicInteger(0)

        // ── Étape 2 : détails en parallèle ──────────────────────────────────
        val semaphore = Semaphore(MAX_CONCURRENT)

        basicList.map { basic ->
            async {
                semaphore.withPermit {
                    val detail = try { fetchDetail(client, basic.rawId) } catch (_: Exception) { null }
                    val entity = if (detail != null) mergeDetail(basic, detail) else basic.toEntity()
                    onProgress?.invoke(doneCount.incrementAndGet(), total)
                    entity
                }
            }
        }.awaitAll()
    }

    // ── Structures internes ────────────────────────────────────────────────

    private data class BasicRadar(
        val rawId: String,
        val lat: Double,
        val lng: Double,
        val type: String
    ) {
        fun toEntity() = RadarEntity(
            id              = stableId(rawId),
            latitude        = lat,
            longitude       = lng,
            type            = type,
            speedLimit      = null,
            speedLimitHgv   = null,
            department      = "",
            city            = "",
            route           = "",
            direction       = "",
            equipment       = "",
            installDate     = "",
            sectionLengthKm = "",
            gridLat         = floor(lat / GRID_SIZE).toInt(),
            gridLng         = floor(lng / GRID_SIZE).toInt(),
            source          = "sr"
        )
    }

    // ── Fetch /radars/all ─────────────────────────────────────────────────

    private fun fetchAllBasic(client: OkHttpClient): List<BasicRadar> {
        val request = Request.Builder()
            .url("$SR_BASE/radars/all")
            .header("Accept", "application/json")
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("SR /radars/all : ${response.code}")
        val json = response.body?.use { it.string() }
            ?: throw IOException("Réponse /radars/all vide")
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { i ->
            val obj   = array.getJSONObject(i)
            val lat   = obj.optDouble("lat", Double.NaN)
            val lng   = obj.optDouble("lng", Double.NaN)
            val rawId = obj.optString("id", "")
            if (lat.isNaN() || lng.isNaN() || rawId.isBlank()) null
            else BasicRadar(rawId, lat, lng, mapType(obj.optString("typeLabel", "")))
        }
    }

    // ── Fetch /radars/{id} ────────────────────────────────────────────────

    /**
     * Tente jusqu'à [DETAIL_RETRIES] fois.
     * Retourne null si toutes les tentatives échouent (le radar sera stocké
     * avec les données de base uniquement).
     */
    private fun fetchDetail(client: OkHttpClient, rawId: String): JSONObject? {
        val url = "$SR_BASE/radars/$rawId"
        var attempt = 0
        while (attempt < DETAIL_RETRIES) {
            attempt++
            try {
                val response = client.newCall(
                    Request.Builder()
                        .url(url)
                        .header("Accept", "application/json")
                        .header("Connection", "close") // force nouvelle connexion, évite "stale" pool
                        .build()
                ).execute()

                if (!response.isSuccessful) { response.close(); return null }

                val bodyStr = response.body?.use { it.string() } ?: continue

                return runCatching { JSONObject(bodyStr) }.getOrNull()
            } catch (_: IOException) {
                // "unexpected end of stream" ou connexion fermée → on retente avec délai
                if (attempt < DETAIL_RETRIES) Thread.sleep(500L * attempt)
            } catch (_: Exception) {
                return null // erreur non-réseau → inutile de retenter
            }
        }
        return null
    }

    // ── Fusion données de base + détail ───────────────────────────────────

    private fun mergeDetail(basic: BasicRadar, d: JSONObject): RadarEntity {

        // Vitesses depuis rulesmesured : macinename = "vitesse_vl_80", "vitesse_pl_90", etc.
        val rules = d.optJSONArray("rulesmesured") ?: JSONArray()
        var speedVl: Int? = null
        var speedPl: Int? = null
        for (i in 0 until rules.length()) {
            val rule  = rules.optJSONObject(i) ?: continue
            val mname = rule.optString("macinename", "")
            when {
                mname.startsWith("vitesse_vl_") ->
                    speedVl = mname.removePrefix("vitesse_vl_").toIntOrNull()
                mname.startsWith("vitesse_pl_") ->
                    speedPl = mname.removePrefix("vitesse_pl_").toIntOrNull()
            }
        }

        // radartronconkm peut être une String "1,8" ou un tableau vide []
        val tronconSection = when (val v = d.opt("radartronconkm")) {
            is String -> v.trim().replace(",", ".")
            else      -> ""
        }

        return RadarEntity(
            id              = stableId(basic.rawId),
            latitude        = basic.lat,
            longitude       = basic.lng,
            type            = basic.type,
            speedLimit      = speedVl,
            speedLimitHgv   = speedPl,
            department      = d.optString("department", ""),
            city            = "",   // non disponible dans le détail
            route           = d.optString("radarroad", ""),
            direction       = d.optString("radardirection", ""),
            equipment       = d.optString("radarequipment", ""),
            installDate     = d.optString("radarinstalldate", ""),
            sectionLengthKm = tronconSection,
            gridLat         = floor(basic.lat / GRID_SIZE).toInt(),
            gridLng         = floor(basic.lng / GRID_SIZE).toInt(),
            source          = "sr"
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Convertit un ID SR string en Long stable sans conflit avec gov (< 100k)
     * ni OSM (< ~13 milliards).
     * - Numérique "143"     → 143 + 100_000_000_000
     * - Alphanum "FE193002" → chiffres seuls + 200_000_000_000
     */
    private fun stableId(rawId: String): Long {
        val numeric = rawId.toLongOrNull()
        if (numeric != null) return numeric + 100_000_000_000L
        val digits = rawId.filter { it.isDigit() }.toLongOrNull()
        if (digits != null) return digits + 200_000_000_000L
        val h = rawId.hashCode().toLong()
        return (if (h < 0) -h else h) + 100_000_000_000L
    }

    private fun mapType(typeLabel: String): String = when (typeLabel) {
        "Fixes"              -> "Radar fixe"
        "Feux rouges"        -> "Radar feu rouge"
        "Vitesse Moyenne"    -> "Radar tronçon"
        "Discriminants"      -> "Radar discriminant"
        "Itinéraires"        -> "Radar itinéraire"
        "Passages à niveau"  -> "Radar passage à niveau"
        "Urbain"             -> "Radar urbain"
        else                 -> typeLabel
    }
}
