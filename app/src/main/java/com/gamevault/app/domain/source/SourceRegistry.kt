package com.gamevault.app.domain.source

/**
 * In-memory registry of available GameSource implementations.
 *
 * Sources register themselves at app startup (via the DI container).
 * Future versions may load sources from external extension APKs.
 */
class SourceRegistry {

    private val sources = mutableMapOf<String, GameSource>()

    /** Register a source keyed by its stable [GameSource.id]. */
    fun register(source: GameSource) {
        sources[source.id] = source
    }

    /** Unregister a source by its stable id. */
    fun unregister(id: String) {
        sources.remove(id)
    }

    /** Look up a source by its stable id. */
    fun get(id: String): GameSource? = sources[id]

    fun getAll(): List<GameSource> = sources.values.toList()
}
