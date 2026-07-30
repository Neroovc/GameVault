package com.gamevault.app.domain.source

/**
 * In-memory registry of available GameSource implementations.
 *
 * Sources register themselves at app startup (via the DI container).
 * Future versions may load sources from external extension APKs.
 */
class SourceRegistry {

    private val sources = mutableMapOf<String, GameSource>()

    fun register(source: GameSource) {
        sources[source.name] = source
    }

    fun unregister(name: String) {
        sources.remove(name)
    }

    fun get(name: String): GameSource? = sources[name]

    fun getAll(): List<GameSource> = sources.values.toList()
}
