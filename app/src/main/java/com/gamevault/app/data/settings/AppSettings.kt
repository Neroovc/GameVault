package com.gamevault.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "gamevault_settings")

enum class ThemeMode(val value: Int) {
    SYSTEM(0),
    LIGHT(1),
    DARK(2);

    companion object {
        fun fromValue(value: Int): ThemeMode =
            entries.firstOrNull { it.value == value } ?: SYSTEM
    }
}

enum class GridMode(val value: Int, val displayName: String) {
    COMPACT(0, "Compact"),
    COMFORTABLE(1, "Comfortable"),
    LIST(2, "List");

    companion object {
        fun fromValue(value: Int): GridMode =
            entries.firstOrNull { it.value == value } ?: COMFORTABLE
    }
}

/**
 * Persisted app settings backed by Jetpack DataStore.
 */
class AppSettings(private val context: Context) {

    private object Keys {
        val THEME_MODE = intPreferencesKey("theme_mode")
        val AMOLED_DARK = booleanPreferencesKey("amoled_dark")
        val GIF_AUTOPLAY = booleanPreferencesKey("gif_autoplay")
        val GRID_MODE = intPreferencesKey("grid_mode")
        val DEFAULT_COLLECTION_ID = longPreferencesKey("default_collection_id")
    }

    /** Observe the current theme mode. Defaults to SYSTEM. */
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        ThemeMode.fromValue(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.value)
    }

    /** Persist a new theme mode. */
    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.value
        }
    }

    /** Observe the AMOLED dark mode setting. Defaults to false. */
    val amoledDark: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.AMOLED_DARK] ?: false
    }

    /** Persist the AMOLED dark mode setting. */
    suspend fun setAmoledDark(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AMOLED_DARK] = enabled
        }
    }

    /** Observe the GIF autoplay setting. Defaults to true. */
    val gifAutoplay: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.GIF_AUTOPLAY] ?: true
    }

    /** Persist the GIF autoplay setting. */
    suspend fun setGifAutoplay(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GIF_AUTOPLAY] = enabled
        }
    }

    /** Observe the grid mode. Defaults to COMFORTABLE. */
    val gridMode: Flow<GridMode> = context.dataStore.data.map { prefs ->
        GridMode.fromValue(prefs[Keys.GRID_MODE] ?: GridMode.COMFORTABLE.value)
    }

    /** Persist the grid mode. */
    suspend fun setGridMode(mode: GridMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GRID_MODE] = mode.value
        }
    }

    /** Default collection where newly added games land. null = library root. */
    val defaultCollectionId: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[Keys.DEFAULT_COLLECTION_ID]?.takeIf { it >= 0 }
    }

    suspend fun setDefaultCollectionId(collectionId: Long?) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DEFAULT_COLLECTION_ID] = collectionId ?: -1L
        }
    }
}
