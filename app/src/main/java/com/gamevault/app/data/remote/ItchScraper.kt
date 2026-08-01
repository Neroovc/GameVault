package com.gamevault.app.data.remote

import com.gamevault.app.domain.model.Game
import com.gamevault.app.domain.model.GameEngine
import com.gamevault.app.domain.model.SourceType
import com.gamevault.app.domain.model.Tag
import com.gamevault.app.domain.source.SearchResult
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import java.net.URLEncoder

/**
 * Scrapes game metadata from itch.io.
 *
 * This is a "best effort" scraper — itch.io's HTML structure may change.
 * We parse known selectors (game_cell / grid_game cards, og:* meta tags)
 * and fall back gracefully when elements are missing. Selectors should be
 * verified against live HTML at build time.
 */
class ItchScraper {

    companion object {
        // Realistic Android Chrome UA to avoid blocks
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/125.0.6422.165 Mobile Safari/537.36"
        private const val TIMEOUT_MS = 30_000L
        private const val BASE_URL = "https://itch.io"
        private const val MAX_TAGS = 12
    }

    /**
     * Search itch.io for games matching a query.
     *
     * Returns the domain [SearchResult] directly (the scraper layer is the
     * only consumer of search results, so no local mirror type is needed).
     */
    suspend fun search(query: String): List<SearchResult> {
        return try {
            val searchUrl = "$BASE_URL/search?q=${URLEncoder.encode(query, "UTF-8")}"
            val doc = fetchDocument(searchUrl)

            doc.select("div.game_cell, div.grid_game").mapNotNull { cell ->
                val linkEl = cell.selectFirst("a.title, .game_title a")
                    ?: cell.selectFirst("a[href*=\".itch.io/\"], a[href*=\"/games/\"]")
                    ?: cell.selectFirst("a[href]")
                val href = linkEl?.attr("href") ?: return@mapNotNull null
                val titleEl = cell.selectFirst(".game_title, a.title, h2")
                val thumbEl = cell.selectFirst(
                    ".game_thumb img, img.game_thumb, img[src*=\"img.itch.zone\"]",
                ) ?: cell.selectFirst("img")

                SearchResult(
                    title = titleEl?.text() ?: linkEl.text() ?: "Unknown",
                    url = normalizeUrl(href, BASE_URL),
                    thumbnailUrl = thumbEl?.let { el ->
                        normalizeUrl(
                            el.attr("src")
                                .ifBlank { el.attr("data-src") }
                                .ifBlank { el.attr("data-lazy_src") },
                            BASE_URL,
                        )
                    },
                    developer = cell.selectFirst(".game_author")?.text()
                        ?.removePrefix("by")?.trim(),
                )
            }.take(25)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Scrape a game info page by URL.
     * @param url Full URL to an itch.io game page (e.g. https://x.itch.io/y)
     */
    suspend fun scrapeGame(url: String): ScrapeResult {
        return try {
            val doc = fetchDocument(url)
            val title = extractTitle(doc) ?: return ScrapeResult.Error("Could not extract title")

            ScrapeResult.Success(
                game = Game(
                    title = title,
                    description = extractDescription(doc),
                    developer = extractDeveloper(doc),
                    engine = extractEngine(doc),
                    version = null,
                    coverUrl = extractCoverUrl(doc, url),
                    f95Url = null,
                    f95Rating = null,
                    sourceType = SourceType.ITCHIO,
                    sourceUrl = url,
                    tags = extractTags(doc),
                ),
                threadId = null,
            )
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            ScrapeResult.Error("Failed to scrape: $msg")
        }
    }

    // ── Private helpers ────────────────────────────────────

    private suspend fun fetchDocument(url: String): Document {
        return Jsoup.connect(url)
            .userAgent(USER_AGENT)
            .timeout(TIMEOUT_MS.toInt())
            .followRedirects(true)
            .get()
    }

    private fun extractTitle(doc: Document): String? {
        // itch.io og:title is usually "GameName by Developer" — strip the byline.
        val ogTitle = doc.selectFirst("meta[property=\"og:title\"]")?.attr("content")?.trim()
        return ogTitle?.substringBefore(" by ")?.trim()?.takeIf { it.isNotEmpty() }
            ?: doc.selectFirst("h1")?.text()?.trim()
            ?: doc.title().removeSuffix(" on itch.io").trim().takeIf { it.isNotEmpty() }
    }

    private fun extractDescription(doc: Document): String? {
        return doc.selectFirst("meta[property=\"og:description\"]")?.attr("content")?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: doc.selectFirst(".formatted_description")?.text()?.trim()?.take(1000)
    }

    private fun extractDeveloper(doc: Document): String? {
        val devInfo = doc.selectFirst(".game_info_panel .dev_info, .dev_info a")
        if (devInfo != null) {
            val text = devInfo.text().removePrefix("by").trim()
            if (text.isNotEmpty()) return text
        }

        doc.selectFirst(".game_author a, .game_author")?.let { el ->
            val text = el.text().removePrefix("by").trim()
            if (text.isNotEmpty()) return text
        }

        // Last resort: og:site_name — skip when it is just the platform name.
        return doc.selectFirst("meta[property=\"og:site_name\"]")?.attr("content")?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals("itch.io", ignoreCase = true) }
    }

    private fun extractEngine(doc: Document): GameEngine? {
        val text = buildString {
            doc.selectFirst(".formatted_description")?.text()?.let { append(it).append('\n') }
            doc.selectFirst(".game_info_panel")?.text()?.let { append(it) }
        }.trim()
        if (text.isEmpty()) return null

        // Same keyword matching as F95ZoneScraper.extractEngine.
        return when {
            text.contains("Ren'Py", ignoreCase = true) ||
                text.contains("renpy", ignoreCase = true) -> GameEngine.RENPY
            text.contains("RPG Maker", ignoreCase = true) ||
                text.contains("RPGM", ignoreCase = true) -> GameEngine.RPGM
            text.contains("Unity", ignoreCase = true) -> GameEngine.UNITY
            text.contains("Unreal", ignoreCase = true) ||
                text.contains("UE4", ignoreCase = true) ||
                text.contains("UE5", ignoreCase = true) -> GameEngine.UNREAL
            text.contains("HTML", ignoreCase = true) -> GameEngine.HTML
            text.contains("Flash", ignoreCase = true) -> GameEngine.FLASH
            text.contains("Java", ignoreCase = true) -> GameEngine.JAVA
            text.contains("Twine", ignoreCase = true) ||
                text.contains("SugarCube", ignoreCase = true) -> GameEngine.TWINE
            else -> GameEngine.OTHER
        }
    }

    private fun extractCoverUrl(doc: Document, pageUrl: String): String? {
        val selectors = listOf(
            "meta[property=\"og:image\"]",
            "meta[name=\"twitter:image\"]",
            ".game_thumb img",
        )

        for (selector in selectors) {
            val el = doc.selectFirst(selector) ?: continue
            val src = el.attr("content")
                .ifBlank { el.attr("src") }
                .ifBlank { el.attr("data-src") }
                .ifBlank { el.attr("data-lazy_src") }
            if (src.isNotBlank()) return normalizeUrl(src, pageUrl)
        }

        return null
    }

    private fun extractTags(doc: Document): List<Tag> {
        val tagNames = doc.select("div.game_tags a[href*='/tag/']")
            .mapNotNull { it.text().trim().takeIf { name -> name.isNotBlank() } }
            .distinct()
            .take(MAX_TAGS)
        return tagNames.map { Tag(name = it) }
    }

    /**
     * Normalizes a possibly-relative URL against [baseUrl].
     * Game pages can live on subdomain hosts (https://X.itch.io/Y), so
     * relative assets are resolved against the page's own scheme+host.
     */
    private fun normalizeUrl(raw: String, baseUrl: String): String {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("https://") || trimmed.startsWith("http://") -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") -> baseOrigin(baseUrl) + trimmed
            else -> try {
                URI(baseUrl).resolve(trimmed).toString()
            } catch (e: Exception) {
                trimmed
            }
        }
    }

    private fun baseOrigin(url: String): String {
        return try {
            val uri = URI(url)
            "${uri.scheme}://${uri.host}"
        } catch (e: Exception) {
            BASE_URL
        }
    }
}
