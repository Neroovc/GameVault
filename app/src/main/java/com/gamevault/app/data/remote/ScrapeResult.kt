package com.gamevault.app.data.remote

import com.gamevault.app.domain.model.Game

/**
 * Shared result type for HTML scrapers.
 *
 * Extracted from [F95ZoneScraper] so multiple scrapers (F95Zone, itch.io)
 * can reuse the same sealed result contract.
 */
sealed class ScrapeResult {
    data class Success(val game: Game, val threadId: String?) : ScrapeResult()
    data class Error(val message: String) : ScrapeResult()
}
