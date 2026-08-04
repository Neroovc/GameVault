package com.gamevault.app.data.remote

import androidx.annotation.DrawableRes
import com.gamevault.app.R
import com.gamevault.app.data.remote.ScrapeResult
import com.gamevault.app.data.settings.AppSettings
import com.gamevault.app.domain.source.GameSource
import com.gamevault.app.domain.source.SearchResult
import com.gamevault.app.domain.source.SourceResult
import com.gamevault.app.domain.model.Game
import com.gamevault.app.domain.model.GameStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull

/**
 * Adapts the existing [F95ZoneScraper] to the [GameSource] interface.
 *
 * This is the reference implementation of GameSource. It wraps the legacy
 * scraper without changing its HTML-parsing internals.
 */
class F95ZoneSource(
    private val scraper: F95ZoneScraper,
    private val appSettings: AppSettings,
) : GameSource {

    override val id: String = "f95zone"

    override val name: String = "F95Zone"

    @get:DrawableRes
    override val iconRes: Int = R.drawable.ic_source_f95zone

    override val description: String = "Adult games forum with the largest game catalogue"

    override suspend fun search(query: String): SourceResult<List<SearchResult>> {
        return try {
            val cookie = appSettings.f95zoneCookie.first()
            val results = scraper.search(query, cookie)
            SourceResult.Success(results.map { sr ->
                SearchResult(
                    title = sr.title,
                    url = sr.url,
                    developer = sr.author,
                )
            })
        } catch (e: ScrapeBlockedException) {
            SourceResult.Error(e.message ?: "Search unavailable", e)
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            SourceResult.Error("Search failed: $msg", e)
        }
    }

    override suspend fun fetchCover(url: String): String? =
        scraper.fetchCover(url, appSettings.f95zoneCookie.firstOrNull())

    override suspend fun fetchDetail(url: String): SourceResult<Game> {
        return try {
            val cookie = appSettings.f95zoneCookie.first()
            when (val result = scraper.scrapeGame(url, cookie)) {
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
