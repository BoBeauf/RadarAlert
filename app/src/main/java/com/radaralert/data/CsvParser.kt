package com.radaralert.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import kotlin.math.floor

object CsvParser {

    private const val CSV_URL =
        "https://www.data.gouv.fr/api/1/datasets/r/8a22b5a8-4b65-41be-891a-7c0aead4ba51"
    private const val GRID_SIZE = 0.05

    // Colonnes du CSV data.gouv.fr (séparé par virgules)
    // 0: date_heure_dernier_changement
    // 1: date_heure_creation
    // 2: departement
    // 3: latitude
    // 4: longitude
    // 5: id
    // 6: direction
    // 7: equipement
    // 8: date_installation
    // 9: type
    // 10: emplacement
    // 11: route
    // 12: longueur_troncon_km
    // 13: vitesse_poids_lourds_kmh
    // 14: vitesse_vehicules_legers_kmh

    suspend fun downloadAndParse(): List<RadarEntity> = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .followRedirects(true)
            .build()

        val request = Request.Builder().url(CSV_URL).build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw IOException("Erreur téléchargement CSV : ${response.code}")
        }

        val body = response.body?.string()
            ?: throw IOException("Réponse vide")

        parse(body)
    }

    fun parse(csvContent: String): List<RadarEntity> {
        val lines = csvContent.lines()
        val radars = mutableListOf<RadarEntity>()
        var lineNum = 0

        for (line in lines) {
            lineNum++
            if (lineNum == 1) continue // Skip header
            if (line.isBlank()) continue

            val fields = parseCsvLine(line)
            if (fields.size < 15) continue

            val lat = fields[3].toDoubleOrNull() ?: continue
            val lng = fields[4].toDoubleOrNull() ?: continue
            val id = fields[5].toLongOrNull() ?: continue

            // Validation coordonnées France métropolitaine + DOM-TOM
            if (lat < -21.4 || lat > 51.2 || lng < -62.0 || lng > 55.9) continue

            radars.add(
                RadarEntity(
                    id = id,
                    latitude = lat,
                    longitude = lng,
                    type = fields[9].trim(),
                    speedLimit = fields[14].trim().toIntOrNull(),
                    speedLimitHgv = fields[13].trim().toIntOrNull(),
                    department = fields[2].trim(),
                    city = fields[10].trim(),
                    route = fields[11].trim(),
                    direction = fields[6].trim(),
                    equipment = fields[7].trim(),
                    installDate = fields[8].trim(),
                    sectionLengthKm = fields[12].trim(),
                    gridLat = floor(lat / GRID_SIZE).toInt(),
                    gridLng = floor(lng / GRID_SIZE).toInt(),
                    source = "gov"
                )
            )
        }

        return radars
    }

    /**
     * Parser CSV simple gérant les champs entre guillemets.
     * Ex: 2022-01-01,Radar fixe,"A6, direction Paris",50
     */
    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    fields.add(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        fields.add(current.toString())
        return fields
    }
}
