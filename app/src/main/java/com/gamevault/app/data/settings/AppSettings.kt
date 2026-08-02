package com.gamevault.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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

enum class StatusStyle(val displayName: String) {
    TOP_BAR("Top bar"),
    BADGE("Badge");
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
        val SHOW_ENGINE = booleanPreferencesKey("show_engine")
        val SHOW_SOURCE = booleanPreferencesKey("show_source")
        val SHOW_STATUS = booleanPreferencesKey("show_status")
        val STATUS_STYLE = stringPreferencesKey("status_style")
        val DEFAULT_COLLECTION_ID = longPreferencesKey("default_collection_id")
        val DISABLED_SOURCE_IDS = stringSetPreferencesKey("disabled_source_ids")
        val F95ZONE_COOKIE = stringPreferencesKey("f95zone_cookie")
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

    /** Observe whether the engine badge is shown on library cards. Defaults to true. */
    val showEngine: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.SHOW_ENGINE] ?: true
    }

    /** Persist the engine badge visibility. */
    suspend fun setShowEngine(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SHOW_ENGINE] = value
        }
    }

    /** Observe whether the source badge is shown on library cards. Defaults to true. */
    val showSource: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.SHOW_SOURCE] ?: true
    }

    /** Persist the source badge visibility. */
    suspend fun setShowSource(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SHOW_SOURCE] = value
        }
    }

    /** Observe the status indicator style on library cards. Defaults to TOP_BAR. */
    val statusStyle: Flow<StatusStyle> = context.dataStore.data.map { prefs ->
        prefs[Keys.STATUS_STYLE]?.let { name ->
            runCatching { StatusStyle.valueOf(name) }.getOrDefault(StatusStyle.TOP_BAR)
        } ?: StatusStyle.TOP_BAR
    }

    /** Persist the status indicator style. */
    suspend fun setStatusStyle(style: StatusStyle) {
        context.dataStore.edit { prefs ->
            prefs[Keys.STATUS_STYLE] = style.name
        }
    }

    /** Observe whether the status strip is shown on library cards. Defaults to true. */
    val showStatus: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.SHOW_STATUS] ?: true
    }

    /** Persist the status strip visibility. */
    suspend fun setShowStatus(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SHOW_STATUS] = value
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

    /**
     * Observe the set of disabled extension source ids.
     * Empty set means ALL sources are enabled.
     */
    val disabledSourceIds: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.DISABLED_SOURCE_IDS] ?: emptySet()
    }

    /** Enable or disable an extension source by id. */
    suspend fun setSourceEnabled(id: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.DISABLED_SOURCE_IDS] ?: emptySet()
            val updated = if (enabled) current - id else current + id
            if (updated.isEmpty()) {
                // DataStore throws on empty string sets — remove the key instead.
                prefs.remove(Keys.DISABLED_SOURCE_IDS)
            } else {
                prefs[Keys.DISABLED_SOURCE_IDS] = updated
            }
        }
    }

    /** Observe the saved F95Zone session cookie. null = no cookie saved. */
    val f95zoneCookie: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.F95ZONE_COOKIE]
    }

    /** Persist (or clear with null) the F95Zone session cookie. */
    suspend fun setF95zoneCookie(cookie: String?) {
        context.dataStore.edit { prefs ->
            if (cookie == null) {
                prefs.remove(Keys.F95ZONE_COOKIE)
            } else {
                prefs[Keys.F95ZONE_COOKIE] = cookie
            }
        }
    }
}
