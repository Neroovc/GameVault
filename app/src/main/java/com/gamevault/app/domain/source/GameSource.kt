package com.gamevault.app.domain.source

import com.gamevault.app.domain.model.Game

/**
 * Result wrapper for source operations.
 */
sealed class SourceResult<out T> {
    data class Success<T>(val data: T) : SourceResult<T>()
    data class Error(val message: String, val exception: Throwable? = null) : SourceResult<Nothing>()
}

/**
 * Lightweight search result from a source — not a full Game domain model.
 */
data class SearchResult(
    val title: String,
    val url: String,
    val thumbnailUrl: String? = null,
    val developer: String? = null,
    val engine: String? = null,
)

/**
 * A game source extension, analogous to Mihon's source extensions.
 *
 * Each implementation knows how to search for and fetch detailed game metadata
 * from a specific provider (F95Zone, Steam, VNDB, etc.).
 */
interface GameSource {
    /** Human-readable name for this source (e.g. "F95Zone"). */
    val name: String

    /** Search the source for games matching [query]. */
    suspend fun search(query: String): SourceResult<List<SearchResult>>

    /** Fetch full game metadata from a source-specific [url]. */
    suspend fun fetchDetail(url: String): SourceResult<Game>
}
