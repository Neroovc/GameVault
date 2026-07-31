package com.gamevault.app.domain.source

import com.gamevault.app.data.settings.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Holders for extension source state: registry access + persisted enabled state.
 *
 * Empty disabled-set in settings means ALL sources are enabled.
 */
class SourceManager(
    private val registry: SourceRegistry,
    private val settings: AppSettings,
) {

    /** All registered sources, regardless of enabled state. */
    val sources: List<GameSource> get() = registry.getAll()

    /** Look up a source by stable id. */
    fun getById(id: String): GameSource? = registry.get(id)

    /** Observe whether a source is enabled. */
    fun isEnabled(id: String): Flow<Boolean> = settings.disabledSourceIds.map { id !in it }

    /** Enable or disable a source by id. */
    suspend fun setEnabled(id: String, enabled: Boolean) = settings.setSourceEnabled(id, enabled)

    /** One-shot enabled check for a source id. */
    suspend fun isEnabledNow(id: String): Boolean = id !in settings.disabledSourceIds.first()

    /** Sources that are currently enabled, observing changes. */
    val enabledSources: Flow<List<GameSource>> = settings.disabledSourceIds.map { disabled ->
        registry.getAll().filter { it.id !in disabled }
    }
}
