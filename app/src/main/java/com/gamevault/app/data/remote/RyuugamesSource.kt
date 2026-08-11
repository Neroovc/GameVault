package com.gamevault.app.data.remote

import androidx.annotation.DrawableRes
import com.gamevault.app.R
import com.gamevault.app.domain.model.Game
import com.gamevault.app.domain.model.GameStatus
import com.gamevault.app.domain.source.GameSource
import com.gamevault.app.domain.source.SearchResult
import com.gamevault.app.domain.source.SourceResult

/**
 * Adapts [RyuugamesScraper] to the [GameSource] interface.
 *
 * Mirrors the structure of [F95ZoneSource].
 */
class RyuugamesSource(
    private val scraper: RyuugamesScraper,
) : GameSource {

    override val id: String = "ryuugames"

    override val name: String = "RyuuGames"

    @get:DrawableRes
    override val iconRes: Int = R.drawable.ic_source_ryuugames

    override val description: String = "Free adult visual novels"

    override suspend fun search(query: String): SourceResult<List<SearchResult>> {
        return try {
            // The scraper already returns domain SearchResults directly.
            SourceResult.Success(scraper.search(query))
        } catch (e: ScrapeBlockedException) {
            SourceResult.Error(e.message ?: "Search unavailable", e)
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            SourceResult.Error("Search failed: $msg", e)
        }
    }

    override suspend fun fetchDetail(url: String): SourceResult<Game> {
        return try {
            when (val result = scraper.scrapeGame(url)) {
                is ScrapeResult.Success -> {
                    // Override the status to NOT_STARTED since it's being imported.
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

    override suspend fun fetchCover(url: String): String? = scraper.fetchCover(url)

    override suspend fun fetchRecent(): SourceResult<List<SearchResult>> {
        return try {
            // The scraper already returns domain SearchResults directly.
            SourceResult.Success(scraper.fetchRecent())
        } catch (e: ScrapeBlockedException) {
            SourceResult.Error(e.message ?: "Recent posts unavailable", e)
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            SourceResult.Error("Failed to fetch recent posts: $msg", e)
        }
    }
}
