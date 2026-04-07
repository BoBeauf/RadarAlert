package com.radaralert.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.floor

object OsmParser {

    private const val OVERPASS_URL = "https://overpass-api.de/api/interpreter"
    // Bounding box Europe entière (35°N–72°N, 11°W–45°E)
    private const val QUERY =
        """[out:json][timeout:360];node["highway"="speed_camera"](35.0,-11.0,72.0,45.0);out body;"""
    private const val GRID_SIZE = 0.05

    suspend fun downloadAndParse(): List<RadarEntity> = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(420, TimeUnit.SECONDS)
            .build()

        val body = FormBody.Builder()
            .add("data", QUERY)
            .build()

        val request = Request.Builder()
            .url(OVERPASS_URL)
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("Erreur Overpass : ${response.code}")
        val json = response.body?.string() ?: throw IOException("Réponse vide")
        parse(json)
    }

    fun parse(json: String): List<RadarEntity> {
        val root = JSONObject(json)
        val elements = root.getJSONArray("elements")
        val result = mutableListOf<RadarEntity>()

        for (i in 0 until elements.length()) {
            val node = elements.getJSONObject(i)
            val lat = node.getDouble("lat")
            val lon = node.getDouble("lon")
            val osmId = node.getLong("id")
            val tags = node.optJSONObject("tags") ?: JSONObject()

            val enforcement = tags.optString("enforcement", "")
            val type = when {
                enforcement.contains("traffic_signals") || enforcement.contains("red_light") ->
                    "Radar feu rouge"
                enforcement.contains("average_speed") || tags.has("average_speed") ->
                    "Radar tronçon"
                else ->
                    "Radar fixe"
            }

            val speedLimit = tags.optString("maxspeed", "").toIntOrNull()
            val speedLimitHgv = tags.optString("maxspeed:hgv", "").toIntOrNull()
            val city = tags.optString("addr:city", "")
            val ref = tags.optString("ref", "")
            val direction = tags.optString("direction", "")
            val name = tags.optString("name", "")

            result.add(
                RadarEntity(
                    id = osmId,
                    latitude = lat,
                    longitude = lon,
                    type = type,
                    speedLimit = speedLimit,
                    speedLimitHgv = speedLimitHgv,
                    department = "",
                    city = if (name.isNotBlank()) name else city,
                    route = ref,
                    direction = direction,
                    equipment = "",
                    installDate = "",
                    sectionLengthKm = "",
                    gridLat = floor(lat / GRID_SIZE).toInt(),
                    gridLng = floor(lon / GRID_SIZE).toInt(),
                    source = "osm"
                )
            )
        }
        return result
    }
}
