package com.gamevault.app.data.remote

import com.gamevault.app.data.remote.ScrapeResult
import com.gamevault.app.domain.source.GameSource
import com.gamevault.app.domain.source.SearchResult
import com.gamevault.app.domain.source.SourceResult
import com.gamevault.app.domain.model.Game
import com.gamevault.app.domain.model.GameStatus

/**
 * Adapts the existing [F95ZoneScraper] to the [GameSource] interface.
 *
 * This is the reference implementation of GameSource. It wraps the legacy
 * scraper without changing its HTML-parsing internals.
 */
class F95ZoneSource(
    private val scraper: F95ZoneScraper,
) : GameSource {

    override val name: String = "F95Zone"

    override suspend fun search(query: String): SourceResult<List<SearchResult>> {
        return try {
            val results = scraper.search(query)
            SourceResult.Success(results.map { sr ->
                SearchResult(
                    title = sr.title,
                    url = sr.url,
                    developer = sr.author,
                )
            })
        } catch (e: Exception) {
            SourceResult.Error("Search failed: ${e.message}", e)
        }
    }

    override suspend fun fetchDetail(url: String): SourceResult<Game> {
        return try {
            when (val result = scraper.scrapeGame(url)) {
                is ScrapeResult.Success -> {
                    // The scraper already populates a Game object from HTML.
                    // Override the status to NEW since it's being imported.
                    SourceResult.Success(result.game.copy(status = GameStatus.NOT_STARTED))
                }
                is ScrapeResult.Error -> {
                    SourceResult.Error(result.message)
                }
            }
        } catch (e: Exception) {
            SourceResult.Error("Failed to fetch detail: ${e.message}", e)
        }
    }
}
