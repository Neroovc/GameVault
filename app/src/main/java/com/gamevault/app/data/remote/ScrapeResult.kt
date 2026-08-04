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

/**
 * Thrown by scrapers when a site answers with a bot-check (CAPTCHA, Cloudflare
 * challenge) or rate-limit page instead of real content.
 *
 * Search methods return plain lists, so adapters catch this type and surface
 * its message as a user-visible error instead of a silent empty result.
 */
class ScrapeBlockedException(message: String) : Exception(message)
