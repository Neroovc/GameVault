package com.gamevault.app.data.remote

import com.gamevault.app.domain.model.Game
import com.gamevault.app.domain.model.GameEngine
import com.gamevault.app.domain.model.SourceType
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Scrapes game metadata from F95Zone threads.
 *
 * This is a "best effort" scraper — F95Zone's HTML structure may change.
 * We parse known selectors and fall back gracefully when elements are missing.
 */
class F95ZoneScraper {

    /**
     * Scrape a game info page by URL.
     * @param url Full URL to an F95Zone game thread (e.g. https://f95zone.to/threads/...)
     * @param cookie Optional session cookie for authenticated content
     */
    suspend fun scrapeGame(url: String, cookie: String? = null): ScrapeResult {
        return try {
            val doc = fetchDocument(url, cookie)
            val title = extractTitle(doc) ?: return ScrapeResult.Error("Could not extract title")
            val threadId = extractThreadId(url)

            ScrapeResult.Success(
                game = Game(
                    title = title,
                    description = extractDescription(doc),
                    developer = extractDeveloper(doc),
                    engine = extractEngine(doc),
                    version = extractVersion(doc),
                    coverUrl = extractCoverUrl(doc),
                    f95Url = url,
                    f95Rating = extractRating(doc),
                    sourceType = SourceType.F95ZONE,
                    sourceUrl = url,
                ),
                threadId = threadId,
            )
        } catch (e: Exception) {
            ScrapeResult.Error("Failed to scrape: ${e.message}")
        }
    }

    /**
     * Search F95Zone for games matching a query.
     * Uses F95Zone's built-in search.
     */
    suspend fun search(query: String, cookie: String? = null): List<SearchResult> {
        return try {
            val searchUrl = "https://f95zone.to/search"
            val doc = fetchDocument("$searchUrl?q=${java.net.URLEncoder.encode(query, "UTF-8")}", cookie)

            doc.select("article[data-author]").mapNotNull { article ->
                val titleEl = article.selectFirst("h3.title a")
                val linkEl = article.selectFirst("a[href*=/threads/]")
                val link = linkEl?.attr("href") ?: return@mapNotNull null

                SearchResult(
                    title = titleEl?.text() ?: "Unknown",
                    url = if (link.startsWith("http")) link else "https://f95zone.to$link",
                    author = article.attr("data-author"),
                    snippet = article.selectFirst(".messageText")?.text(),
                )
            }.take(20)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ── Private helpers ────────────────────────────────────

    private suspend fun fetchDocument(url: String, cookie: String?): Document {
        val conn = Jsoup.connect(url)
            .userAgent(USER_AGENT)
            .timeout(15_000)
            .followRedirects(true)

        if (cookie != null) {
            conn.cookie("xf_session", cookie)
        }

        return conn.get()
    }

    private fun extractTitle(doc: Document): String? {
        // F95Zone thread title is typically in the <title> tag or h1
        return doc.selectFirst("h1.p-title-value")?.text()
            ?: doc.title().removeSuffix(" | F95zone").trim()
    }

    private fun extractDescription(doc: Document): String? {
        // First message content (the OP)
        return doc.selectFirst("article.message-body .bbWrapper")?.text()
            ?.take(1000)
    }

    private fun extractDeveloper(doc: Document): String? {
        // Common patterns in F95 threads
        val text = doc.selectFirst("article.message-body")?.text() ?: return null
        val patterns = listOf(
            Regex("(?:Developer|Developer\\(s\\)|Author|Creator)[:\\s]+([^\\n,]+)", RegexOption.IGNORE_CASE),
            Regex("by\\s+([A-Za-z0-9_\\s]+)", RegexOption.IGNORE_CASE),
        )
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                val dev = match.groupValues[1].trim()
                if (dev.length in 2..50) return dev
            }
        }
        return null
    }

    private fun extractEngine(doc: Document): GameEngine? {
        val text = doc.selectFirst("article.message-body")?.text() ?: return null
        return when {
            text.contains("Ren'Py", ignoreCase = true) ||
            text.contains("Renpy", ignoreCase = true) -> GameEngine.RENPY
            text.contains("RPG Maker", ignoreCase = true) ||
            text.contains("RPGM", ignoreCase = true) -> GameEngine.RPGM
            text.contains("Unity", ignoreCase = true) -> GameEngine.UNITY
            text.contains("Unreal", ignoreCase = true) ||
            text.contains("UE4", ignoreCase = true) -> GameEngine.UNREAL
            text.contains("HTML", ignoreCase = true) -> GameEngine.HTML
            text.contains("Flash", ignoreCase = true) ||
            text.contains("SWF", ignoreCase = true) -> GameEngine.FLASH
            text.contains("Java", ignoreCase = true) -> GameEngine.JAVA
            text.contains("Twine", ignoreCase = true) ||
            text.contains("SugarCube", ignoreCase = true) -> GameEngine.TWINE
            text.contains("RAGS", ignoreCase = true) ||
            text.contains("TIC-80", ignoreCase = true) ||
            text.contains("PICO-8", ignoreCase = true) -> GameEngine.OTHER
            else -> GameEngine.UNKNOWN
        }
    }

    private fun extractVersion(doc: Document): String? {
        // Look for version patterns in the thread
        val text = doc.selectFirst("article.message-body")?.text() ?: return null
        val versionPattern = Regex("""(?:Version|v|Ver)[.:\s]*(\d+[\d.]*\d*)""", RegexOption.IGNORE_CASE)
        return versionPattern.find(text)?.groupValues?.get(1)
    }

    private fun extractCoverUrl(doc: Document): String? {
        // Find the first attached image or cover image
        val attachment = doc.selectFirst("a[href*=/attachments/] img")
            ?: doc.selectFirst(".bbImage")
        return attachment?.attr("src")?.let { normalizeUrl(it) }
            ?: attachment?.attr("data-src")?.let { normalizeUrl(it) }
    }

    private fun extractRating(doc: Document): Float? {
        // F95Zone rating stars (if visible)
        val ratingEl = doc.selectFirst(".rating-stars") ?: return null
        val style = ratingEl.attr("style")
        val widthMatch = Regex("width\\s*:\\s*(\\d+(\\.\\d+)?)%").find(style)
        return widthMatch?.groupValues?.get(1)?.toFloatOrNull()
            ?.let { (it / 100) * 5 }
            ?.let { (it * 2).toInt() / 2f }
    }

    private fun extractThreadId(url: String): String? {
        val match = Regex("""\.(\d+)/""").find(url)
        return match?.groupValues?.get(1)
    }

    private fun normalizeUrl(url: String): String {
        return when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> "https://f95zone.to$url"
            else -> url
        }
    }

    companion object {
        private const val USER_AGENT = "GameVault/0.1 (Android Game Manager; +https://github.com/gamevault)"
    }
}

// ── Result types ──────────────────────────────────────────

sealed class ScrapeResult {
    data class Success(val game: Game, val threadId: String?) : ScrapeResult()
    data class Error(val message: String) : ScrapeResult()
}

data class SearchResult(
    val title: String,
    val url: String,
    val author: String? = null,
    val snippet: String? = null,
)
