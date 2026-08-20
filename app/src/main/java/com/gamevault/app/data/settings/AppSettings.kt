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
import kotlinx.coroutines.flow.first
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

enum class ColorPalette(val key: String, val displayName: String) {
    VIOLET("violet", "Violet"),
    SUNSET("sunset", "Sunset"),
    OCEAN("ocean", "Ocean"),
    FOREST("forest", "Forest"),
    GOLD("gold", "Gold");

    companion object {
        fun fromValue(value: String): ColorPalette =
            entries.firstOrNull { it.key == value } ?: VIOLET
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

enum class RatingStyle(val displayName: String) {
    STAR("Star"),
    NUMBER("Number");
}

enum class SourceRequestPace(
    val value: Int,
    val displayName: String,
    val minPageIntervalMs: Long,
    val maxConcurrent: Int,
    val minBraveIntervalMs: Long,
) {
    GENTLE(0, "Gentle", 1_600L, 1, 2_500L),
    NORMAL(1, "Normal", 900L, 2, 1_500L);

    companion object {
        fun fromValue(value: Int): SourceRequestPace =
            entries.firstOrNull { it.value == value } ?: GENTLE
    }
}

/**
 * How often the automatic F95Zone update check runs.
 * [intervalMillis] is null when checks are disabled.
 */
enum class UpdateCheckInterval(
    val value: Int,
    val displayName: String,
    val intervalMillis: Long?,
) {
    OFF(0, "Off", null),
    HOURS_6(6, "6 hours", 6L * 3_600_000L),
    HOURS_12(12, "12 hours", 12L * 3_600_000L),
    HOURS_24(24, "24 hours", 24L * 3_600_000L),
    HOURS_48(48, "48 hours", 48L * 3_600_000L),
    HOURS_168(168, "7 days", 168L * 3_600_000L);

    companion object {
        fun fromValue(value: Int): UpdateCheckInterval =
            entries.firstOrNull { it.value == value } ?: HOURS_12
    }
}

/**
 * Immutable snapshot of the persisted app settings, used for backup and restore.
 * Nullable fields mean "not exported" or "do not touch on restore".
 */
data class AppSettingsSnapshot(
    val themeMode: Int? = null,
    val colorPalette: String? = null,
    val amoledDark: Boolean? = null,
    val gifAutoplay: Boolean? = null,
    val gridMode: Int? = null,
    val showEngine: Boolean? = null,
    val showSource: Boolean? = null,
    val statusStyle: String? = null,
    val ratingStyle: String? = null,
    val defaultCollectionId: Long? = null,
    val disabledSourceIds: Set<String>? = null,
    val f95zoneCookie: String? = null,
    val ryuugamesCfCookie: String? = null,
    val sourceRequestPace: Int? = null,
    val updateCheckInterval: Int? = null,
    val incognitoMode: Boolean? = null,
)

/**
 * Persisted app settings backed by Jetpack DataStore.
 */
class AppSettings(private val context: Context) {

    private object Keys {
        val THEME_MODE = intPreferencesKey("theme_mode")
        val COLOR_PALETTE = stringPreferencesKey("color_palette")
        val AMOLED_DARK = booleanPreferencesKey("amoled_dark")
        val GIF_AUTOPLAY = booleanPreferencesKey("gif_autoplay")
        val GRID_MODE = intPreferencesKey("grid_mode")
        val SHOW_ENGINE = booleanPreferencesKey("show_engine")
        val SHOW_SOURCE = booleanPreferencesKey("show_source")
        val SHOW_STATUS = booleanPreferencesKey("show_status")
        val STATUS_STYLE = stringPreferencesKey("status_style")
        val RATING_STYLE = stringPreferencesKey("rating_style")
        val DEFAULT_COLLECTION_ID = longPreferencesKey("default_collection_id")
        val DISABLED_SOURCE_IDS = stringSetPreferencesKey("disabled_source_ids")
        val F95ZONE_COOKIE = stringPreferencesKey("f95zone_cookie")
        val RYUUGAMES_CF_COOKIE = stringPreferencesKey("ryuugames_cf_cookie")
        val SOURCE_REQUEST_PACE = intPreferencesKey("source_request_pace")
        val UPDATE_CHECK_INTERVAL = intPreferencesKey("update_check_interval")
        val INCOGNITO_MODE = booleanPreferencesKey("incognito_mode")
    }

    private val LEGACY_SHOW_ENGINE_SOURCE = booleanPreferencesKey("show_engine_source")

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

    /** Observe the selected color palette. Defaults to VIOLET. */
    val colorPalette: Flow<ColorPalette> = context.dataStore.data.map { prefs ->
        ColorPalette.fromValue(prefs[Keys.COLOR_PALETTE] ?: ColorPalette.VIOLET.key)
    }

    /** Persist the selected color palette. */
    suspend fun setColorPalette(palette: ColorPalette) {
        context.dataStore.edit { prefs ->
            prefs[Keys.COLOR_PALETTE] = palette.key
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
        prefs[Keys.SHOW_ENGINE] ?: prefs[LEGACY_SHOW_ENGINE_SOURCE] ?: true
    }

    /** Persist the engine badge visibility. */
    suspend fun setShowEngine(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SHOW_ENGINE] = value
        }
    }

    /** Observe whether the source badge is shown on library cards. Defaults to true. */
    val showSource: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.SHOW_SOURCE] ?: prefs[LEGACY_SHOW_ENGINE_SOURCE] ?: true
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

    /** Observe the rating badge style on library cards. Defaults to STAR. */
    val ratingStyle: Flow<RatingStyle> = context.dataStore.data.map { prefs ->
        prefs[Keys.RATING_STYLE]?.let { name ->
            runCatching { RatingStyle.valueOf(name) }.getOrDefault(RatingStyle.STAR)
        } ?: RatingStyle.STAR
    }

    /** Persist the rating badge style. */
    suspend fun setRatingStyle(style: RatingStyle) {
        context.dataStore.edit { prefs ->
            prefs[Keys.RATING_STYLE] = style.name
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

    /** Observe the saved RyuuGames Cloudflare clearance cookie. null = no cookie saved. */
    val ryuugamesCfCookie: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.RYUUGAMES_CF_COOKIE]
    }

    /** Persist (or clear with null) the RyuuGames Cloudflare clearance cookie. */
    suspend fun setRyuugamesCfCookie(cookie: String?) {
        context.dataStore.edit { prefs ->
            if (cookie == null) {
                prefs.remove(Keys.RYUUGAMES_CF_COOKIE)
            } else {
                prefs[Keys.RYUUGAMES_CF_COOKIE] = cookie
            }
        }
    }

    /** Observe the F95Zone request pace. Defaults to GENTLE (rate-limit safe). */
    val sourceRequestPace: Flow<SourceRequestPace> = context.dataStore.data.map { prefs ->
        SourceRequestPace.fromValue(prefs[Keys.SOURCE_REQUEST_PACE] ?: SourceRequestPace.GENTLE.value)
    }

    /** Persist the F95Zone request pace. */
    suspend fun setSourceRequestPace(pace: SourceRequestPace) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SOURCE_REQUEST_PACE] = pace.value
        }
    }

    /** Observe the automatic update check interval. Defaults to HOURS_12. */
    val updateCheckInterval: Flow<UpdateCheckInterval> = context.dataStore.data.map { prefs ->
        UpdateCheckInterval.fromValue(
            prefs[Keys.UPDATE_CHECK_INTERVAL] ?: UpdateCheckInterval.HOURS_12.value
        )
    }

    /** Persist the automatic update check interval. */
    suspend fun setUpdateCheckInterval(interval: UpdateCheckInterval) {
        context.dataStore.edit { prefs ->
            prefs[Keys.UPDATE_CHECK_INTERVAL] = interval.value
        }
    }

    /** Observe incognito mode. When enabled, play sessions are NOT recorded. Defaults to false. */
    val incognitoMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.INCOGNITO_MODE] ?: false
    }

    /** Persist incognito mode. */
    suspend fun setIncognitoMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.INCOGNITO_MODE] = enabled
        }
    }

    /**
     * Capture the current persisted values. Cookies are only captured when [includeCookies] is true.
     */
    suspend fun snapshot(includeCookies: Boolean = true): AppSettingsSnapshot {
        val prefs = context.dataStore.data.first()
        return AppSettingsSnapshot(
            themeMode = prefs[Keys.THEME_MODE],
            colorPalette = prefs[Keys.COLOR_PALETTE],
            amoledDark = prefs[Keys.AMOLED_DARK],
            gifAutoplay = prefs[Keys.GIF_AUTOPLAY],
            gridMode = prefs[Keys.GRID_MODE],
            showEngine = prefs[Keys.SHOW_ENGINE],
            showSource = prefs[Keys.SHOW_SOURCE],
            statusStyle = prefs[Keys.STATUS_STYLE],
            ratingStyle = prefs[Keys.RATING_STYLE],
            defaultCollectionId = prefs[Keys.DEFAULT_COLLECTION_ID],
            disabledSourceIds = prefs[Keys.DISABLED_SOURCE_IDS],
            f95zoneCookie = if (includeCookies) prefs[Keys.F95ZONE_COOKIE] else null,
            ryuugamesCfCookie = if (includeCookies) prefs[Keys.RYUUGAMES_CF_COOKIE] else null,
            sourceRequestPace = prefs[Keys.SOURCE_REQUEST_PACE],
            updateCheckInterval = prefs[Keys.UPDATE_CHECK_INTERVAL],
            incognitoMode = prefs[Keys.INCOGNITO_MODE],
        )
    }

    /**
     * Apply the non-null fields of [snapshot] to the persisted settings.
     * Null fields are left untouched so partial backups never overwrite
     * values that were not exported.
     */
    suspend fun applySnapshot(snapshot: AppSettingsSnapshot) {
        context.dataStore.edit { prefs ->
            snapshot.themeMode?.let { prefs[Keys.THEME_MODE] = it }
            snapshot.colorPalette?.let { prefs[Keys.COLOR_PALETTE] = it }
            snapshot.amoledDark?.let { prefs[Keys.AMOLED_DARK] = it }
            snapshot.gifAutoplay?.let { prefs[Keys.GIF_AUTOPLAY] = it }
            snapshot.gridMode?.let { prefs[Keys.GRID_MODE] = it }
            snapshot.showEngine?.let { prefs[Keys.SHOW_ENGINE] = it }
            snapshot.showSource?.let { prefs[Keys.SHOW_SOURCE] = it }
            snapshot.statusStyle?.let { prefs[Keys.STATUS_STYLE] = it }
            snapshot.ratingStyle?.let { prefs[Keys.RATING_STYLE] = it }
            snapshot.defaultCollectionId?.let { prefs[Keys.DEFAULT_COLLECTION_ID] = it }
            // DataStore throws on empty string sets — skip the write instead of
            // storing an empty set (mirrors setSourceEnabled semantics).
            if (!snapshot.disabledSourceIds.isNullOrEmpty()) {
                prefs[Keys.DISABLED_SOURCE_IDS] = snapshot.disabledSourceIds
            }
            snapshot.f95zoneCookie?.let { prefs[Keys.F95ZONE_COOKIE] = it }
            snapshot.ryuugamesCfCookie?.let { prefs[Keys.RYUUGAMES_CF_COOKIE] = it }
            snapshot.sourceRequestPace?.let { prefs[Keys.SOURCE_REQUEST_PACE] = it }
            snapshot.updateCheckInterval?.let { prefs[Keys.UPDATE_CHECK_INTERVAL] = it }
            snapshot.incognitoMode?.let { prefs[Keys.INCOGNITO_MODE] = it }
        }
    }
}
