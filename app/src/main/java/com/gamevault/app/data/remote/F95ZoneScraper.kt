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

    companion object {
        // Realistic Android Chrome UA to avoid blocks
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/125.0.6422.165 Mobile Safari/537.36"
        private const val TIMEOUT_MS = 30_000L
    }

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
            val msg = e.message ?: e.javaClass.simpleName
            ScrapeResult.Error("Failed to scrape: $msg")
        }
    }

    /**
     * Search F95Zone for games matching a query.
     * Uses F95Zone's built-in search.
     */
    suspend fun search(query: String, cookie: String? = null): List<SearchResult> {
        return try {
            val searchUrl = "https://f95zone.to/search"
            val doc = fetchDocument(
                "$searchUrl?q=${java.net.URLEncoder.encode(query, "UTF-8")}", cookie,
            )

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
            .timeout(TIMEOUT_MS.toInt())
            .followRedirects(true)

        if (cookie != null) {
            conn.cookie("xf_session", cookie)
        }

        return conn.get()
    }

    private fun extractTitle(doc: Document): String? {
        return doc.selectFirst("h1.p-title-value")?.text()
            ?: doc.title().removeSuffix(" | F95zone").trim()
    }

    private fun extractDescription(doc: Document): String? {
        return doc.selectFirst("article.message-body .bbWrapper")?.text()
            ?.take(1000)
    }

    private fun extractDeveloper(doc: Document): String? {
        // F95Zone often lists the OP author as the developer
        return doc.selectFirst("article[data-author]")?.attr("data-author")
    }

    private fun extractEngine(doc: Document): GameEngine? {
        val body = doc.selectFirst("article.message-body .bbWrapper")?.text() ?: return null

        return when {
            body.contains("Ren'Py", ignoreCase = true) ||
                body.contains("renpy", ignoreCase = true) -> GameEngine.RENPY
            body.contains("RPG Maker", ignoreCase = true) ||
                body.contains("RPGM", ignoreCase = true) -> GameEngine.RPGM
            body.contains("Unity", ignoreCase = true) -> GameEngine.UNITY
            body.contains("Unreal", ignoreCase = true) ||
                body.contains("UE4", ignoreCase = true) ||
                body.contains("UE5", ignoreCase = true) -> GameEngine.UNREAL
            body.contains("HTML", ignoreCase = true) -> GameEngine.HTML
            body.contains("Flash", ignoreCase = true) -> GameEngine.FLASH
            body.contains("Java", ignoreCase = true) -> GameEngine.JAVA
            body.contains("Twine", ignoreCase = true) ||
                body.contains("SugarCube", ignoreCase = true) -> GameEngine.TWINE
            else -> GameEngine.OTHER
        }
    }

    private fun extractVersion(doc: Document): String? {
        val body = doc.selectFirst("article.message-body .bbWrapper")?.text() ?: return null

        // Common patterns: "Version: 1.0", "v1.0.0", "Current version 0.5a"
        val patterns = listOf(
            Regex("""[Vv]ersion[:\s]*([\d.]+[a-zA-Z]*)"""),
            Regex("""[Cc]urrent\s+version[:\s]*([\d.]+[a-zA-Z]*)"""),
            Regex("""\b(v[\d]+\.[\d]+[\w.]*)\b"""),
        )

        for (pattern in patterns) {
            val match = pattern.find(body)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        return null
    }

    private fun extractCoverUrl(doc: Document): String? {
        // Prioritise images embedded in the post body (actual game covers/screenshots)
        // before falling back to the thread's og:image (usually a banner/title card).
        val selectors = listOf(
            // First attachment image inside the post body (the OP's first image)
            "article.message-body .bbWrapper a[href*=\"attachments\"] img",
            // Direct image in the post body
            "article.message-body .bbWrapper img[src*=\"attachments\"]",
            // Any linked attachment image
            "a[href*=\"attachments\"] img",
            // Other images inside the message
            ".message-content img",
            // Fallback: thread metadata banner
            "meta[property=\"og:image\"]",
            // Last resort: any meta image
            "meta[property=\"twitter:image\"]",
        )

        for (selector in selectors) {
            val el = doc.selectFirst(selector) ?: continue
            val url = when {
                selector.startsWith("meta") -> el.attr("content")
                else -> {
                    val src = el.attr("src")
                    // Skip icons / smilies / emoji (they're tiny)
                    if (src.contains("/data/") || src.contains("attachments")) src else ""
                }
            }
            if (url.isNotBlank()) return normalizeUrl(url)
        }
        return null
    }

    private fun extractRating(doc: Document): Float? {
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
