package app.novapill.android.core

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.novaDataStore by preferencesDataStore(name = "nova_pill_settings")

 data class AppSettings(
    val setupComplete: Boolean = false,
    val welcomeSeen: Boolean = false,
    val notificationPeek: Boolean = true,
    val privatePreview: Boolean = true,
    val visualizerEnabled: Boolean = true,
    val reducedMotion: Boolean = false,
    val notificationDurationSeconds: Int = 2,
    val importedLrc: String = "",
    val manualRecordingIndicator: Boolean = false,
    val selectedWeatherCity: String = "",
    val islandWidthDp: Int = 184,
    val topOffsetDp: Int = 7,
    val lyricsSyncOffsetMs: Int = 0,
    val hideWhenIdle: Boolean = true
)

class AppSettingsStore(context: Context) {
    private val dataStore = context.applicationContext.novaDataStore

    val settings: Flow<AppSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error
        }
        .map { prefs ->
            AppSettings(
                setupComplete = prefs[Keys.setupComplete] ?: false,
                welcomeSeen = prefs[Keys.welcomeSeen] ?: false,
                notificationPeek = prefs[Keys.notificationPeek] ?: true,
                privatePreview = prefs[Keys.privatePreview] ?: true,
                visualizerEnabled = prefs[Keys.visualizerEnabled] ?: true,
                reducedMotion = prefs[Keys.reducedMotion] ?: false,
                notificationDurationSeconds = normalizeDuration(prefs[Keys.notificationDuration] ?: 2),
                importedLrc = prefs[Keys.importedLrc] ?: "",
                manualRecordingIndicator = prefs[Keys.manualRecording] ?: false,
                selectedWeatherCity = prefs[Keys.weatherCity] ?: "",
                islandWidthDp = (prefs[Keys.islandWidthDp] ?: 184).coerceIn(154, 210),
                topOffsetDp = (prefs[Keys.topOffsetDp] ?: 7).coerceIn(0, 32),
                lyricsSyncOffsetMs = (prefs[Keys.lyricsSyncOffsetMs] ?: 0).coerceIn(-1500, 1500),
                hideWhenIdle = prefs[Keys.hideWhenIdle] ?: true
            )
        }

    suspend fun setSetupComplete(value: Boolean) = dataStore.edit { it[Keys.setupComplete] = value }
    suspend fun setWelcomeSeen(value: Boolean) = dataStore.edit { it[Keys.welcomeSeen] = value }
    suspend fun setNotificationPeek(value: Boolean) = dataStore.edit { it[Keys.notificationPeek] = value }
    suspend fun setPrivatePreview(value: Boolean) = dataStore.edit { it[Keys.privatePreview] = value }
    suspend fun setVisualizerEnabled(value: Boolean) = dataStore.edit { it[Keys.visualizerEnabled] = value }
    suspend fun setReducedMotion(value: Boolean) = dataStore.edit { it[Keys.reducedMotion] = value }
    suspend fun setNotificationDuration(value: Int) = dataStore.edit { it[Keys.notificationDuration] = normalizeDuration(value) }
    suspend fun setImportedLrc(value: String) = dataStore.edit { it[Keys.importedLrc] = value.take(300_000) }
    suspend fun setManualRecording(value: Boolean) = dataStore.edit { it[Keys.manualRecording] = value }
    suspend fun setWeatherCity(value: String) = dataStore.edit { it[Keys.weatherCity] = value.take(80) }
    suspend fun setIslandWidth(value: Int) = dataStore.edit { it[Keys.islandWidthDp] = value.coerceIn(154, 210) }
    suspend fun setTopOffset(value: Int) = dataStore.edit { it[Keys.topOffsetDp] = value.coerceIn(0, 32) }
    suspend fun setLyricsSyncOffset(value: Int) = dataStore.edit { it[Keys.lyricsSyncOffsetMs] = value.coerceIn(-1500, 1500) }
    suspend fun setHideWhenIdle(value: Boolean) = dataStore.edit { it[Keys.hideWhenIdle] = value }

    private fun normalizeDuration(value: Int): Int = listOf(2, 3, 4, 6).minBy { kotlin.math.abs(it - value) }

    private object Keys {
        val setupComplete = booleanPreferencesKey("setup_complete")
        val welcomeSeen = booleanPreferencesKey("welcome_seen")
        val notificationPeek = booleanPreferencesKey("notification_peek")
        val privatePreview = booleanPreferencesKey("private_preview")
        val visualizerEnabled = booleanPreferencesKey("visualizer_enabled")
        val reducedMotion = booleanPreferencesKey("reduced_motion")
        val notificationDuration = intPreferencesKey("notification_duration")
        val importedLrc = stringPreferencesKey("imported_lrc")
        val manualRecording = booleanPreferencesKey("manual_recording")
        val weatherCity = stringPreferencesKey("weather_city")
        val islandWidthDp = intPreferencesKey("island_width_dp")
        val topOffsetDp = intPreferencesKey("top_offset_dp")
        val lyricsSyncOffsetMs = intPreferencesKey("lyrics_sync_offset_ms")
        val hideWhenIdle = booleanPreferencesKey("hide_when_idle")
    }
}
