package com.radaralert.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val ALERT_DISTANCE_KEY = intPreferencesKey("alert_distance_meters")
        val SOUND_ENABLED_KEY = booleanPreferencesKey("sound_enabled")
        val LAST_DATA_UPDATE_KEY = longPreferencesKey("last_data_update")
        val OVERLAY_X_KEY = intPreferencesKey("overlay_x")
        val OVERLAY_Y_KEY = intPreferencesKey("overlay_y")
        val OVERLAY_STYLE_KEY = stringPreferencesKey("overlay_style")
        val GOV_ENABLED_KEY = booleanPreferencesKey("gov_enabled")
        val OSM_ENABLED_KEY = booleanPreferencesKey("osm_enabled")
        val SR_ENABLED_KEY = booleanPreferencesKey("sr_enabled")
    }

    val alertDistance: Flow<Int> = context.dataStore.data.map {
        it[ALERT_DISTANCE_KEY] ?: 500
    }

    val soundEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[SOUND_ENABLED_KEY] ?: true
    }

    val lastDataUpdate: Flow<Long> = context.dataStore.data.map {
        it[LAST_DATA_UPDATE_KEY] ?: 0L
    }

    val overlayX: Flow<Int> = context.dataStore.data.map { it[OVERLAY_X_KEY] ?: 0 }
    val overlayY: Flow<Int> = context.dataStore.data.map { it[OVERLAY_Y_KEY] ?: 80 }
    val overlayStyle: Flow<String> = context.dataStore.data.map { it[OVERLAY_STYLE_KEY] ?: "text" }
    val govEnabled: Flow<Boolean> = context.dataStore.data.map { it[GOV_ENABLED_KEY] ?: true }
    val osmEnabled: Flow<Boolean> = context.dataStore.data.map { it[OSM_ENABLED_KEY] ?: false }
    val srEnabled: Flow<Boolean> = context.dataStore.data.map { it[SR_ENABLED_KEY] ?: false }

    suspend fun setAlertDistance(meters: Int) {
        context.dataStore.edit { it[ALERT_DISTANCE_KEY] = meters }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[SOUND_ENABLED_KEY] = enabled }
    }

    suspend fun setLastDataUpdate(timestamp: Long) {
        context.dataStore.edit { it[LAST_DATA_UPDATE_KEY] = timestamp }
    }

    suspend fun setOverlayPosition(x: Int, y: Int) {
        context.dataStore.edit {
            it[OVERLAY_X_KEY] = x
            it[OVERLAY_Y_KEY] = y
        }
    }

    suspend fun setOverlayStyle(style: String) {
        context.dataStore.edit { it[OVERLAY_STYLE_KEY] = style }
    }

    suspend fun setGovEnabled(enabled: Boolean) {
        context.dataStore.edit { it[GOV_ENABLED_KEY] = enabled }
    }

    suspend fun setOsmEnabled(enabled: Boolean) {
        context.dataStore.edit { it[OSM_ENABLED_KEY] = enabled }
    }

    suspend fun setSrEnabled(enabled: Boolean) {
        context.dataStore.edit { it[SR_ENABLED_KEY] = enabled }
    }
}
