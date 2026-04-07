package com.radaralert.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RadarDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(radars: List<RadarEntity>)

    @Query("""
        SELECT * FROM radars
        WHERE gridLat BETWEEN :minGridLat AND :maxGridLat
        AND gridLng BETWEEN :minGridLng AND :maxGridLng
    """)
    suspend fun getRadarsInGridRange(
        minGridLat: Int,
        maxGridLat: Int,
        minGridLng: Int,
        maxGridLng: Int
    ): List<RadarEntity>

    @Query("SELECT COUNT(*) FROM radars")
    suspend fun getCount(): Int

    @Query("SELECT * FROM radars LIMIT 1")
    suspend fun getFirstRadar(): RadarEntity?

    @Query("DELETE FROM radars")
    suspend fun deleteAll()

    @Query("DELETE FROM radars WHERE source = :source")
    suspend fun deleteBySource(source: String)

    @Query("SELECT COUNT(*) FROM radars WHERE source = :source")
    suspend fun getCountBySource(source: String): Int
}
