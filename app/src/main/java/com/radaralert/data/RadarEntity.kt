package com.radaralert.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "radars",
    indices = [
        Index(value = ["gridLat", "gridLng"])
    ]
)
data class RadarEntity(
    @PrimaryKey val id: Long,
    val latitude: Double,
    val longitude: Double,
    val type: String,           // "Radar fixe", "Radar feu rouge", etc.
    val speedLimit: Int?,       // vitesse véhicules légers (null si non renseignée)
    val speedLimitHgv: Int?,    // vitesse poids lourds (gov uniquement)
    val department: String,
    val city: String,           // colonne "emplacement"
    val route: String,
    val direction: String,
    val equipment: String,      // marque/modèle de l'équipement (gov, sr)
    val installDate: String,    // date d'installation ISO (gov)
    val sectionLengthKm: String,// longueur du tronçon en km (gov, radars tronçon)
    // Cellule de grille pré-calculée pour indexation spatiale
    val gridLat: Int,           // floor(latitude / 0.05).toInt()
    val gridLng: Int,           // floor(longitude / 0.05).toInt()
    val source: String          // "gov" = data.gouv.fr, "osm" = OpenStreetMap, "sr" = radars.securite-routiere.gouv.fr
)
