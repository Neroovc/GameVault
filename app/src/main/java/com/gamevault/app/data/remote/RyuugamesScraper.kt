package com.gamevault.app.data.remote

import com.gamevault.app.domain.model.Game
import com.gamevault.app.domain.model.GameEngine
import com.gamevault.app.domain.model.SourceType
import com.gamevault.app.domain.model.Tag
import com.gamevault.app.domain.source.SearchResult
import org.jsoup.Connection
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.net.URI
import java.net.URLEncoder

/**
 * Scrapes game metadata from RyuuGames (https://www.ryuugames.com).
 *
 * Search must go through a POST: plain GET /?s=QUERY is Cloudflare-challenged
 * (403), while POSTing the form field `s` to the homepage returns 200 with
 * results. Pagination reuses the same POST against /page/N/ (verified live:
 * pages 2 and 3 return 15 cards each); WordPress 404s once results run out,
 * so an out-of-range page stops the loop, while 403/429 or a Cloudflare
 * challenge shell surfaces as [ScrapeBlockedException] instead of an empty
 * result. A failed first-page POST falls back to a plain GET /?s=QUERY once.
 *
 * On list cards the src attribute is a base64 placeholder, so card
 * thumbnails must use data-img-url. Result hrefs may
 * carry ?_rt=...&_rt_nonce=... tracking params, which are stripped.
 *
 * Not required by the GameSource contract, but category pages like
 * https://www.ryuugames.com/category/visualnovel/english-translated/ work
 * with a plain GET if browse support is ever needed.
 */
class RyuugamesScraper {

    companion object {
        // Realistic Android Chrome UA to avoid blocks
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/125.0.6422.165 Mobile Safari/537.36"
        private const val TIMEOUT_MS = 30_000L
        private const val BASE_URL = "https://www.ryuugames.com"
        private const val MAX_PAGES = 5
        private const val MAX_TAGS = 12
    }

    /**
     * Search RyuuGames for games matching a query.
     *
     * Returns the domain [SearchResult] directly (the scraper layer is the
     * only consumer of search results, so no local mirror type is needed).
     *
     * Failures are NOT silent: 403/429 answers and Cloudflare challenge shells
     * throw [ScrapeBlockedException] (surfaced by the adapter as an error).
     * Only a clean page with zero matching cards yields an empty list.
     */
    suspend fun search(query: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        var page = 1
        while (page <= MAX_PAGES) {
            val pageUrl = if (page == 1) BASE_URL else "$BASE_URL/page/$page/"
            val cards = parseCards(fetchSearchPage(pageUrl, query))
            if (cards.isEmpty()) break
            results += cards
            page++
        }
        return results
    }

    /**
     * Scrape a game info page by URL.
     * @param url Full URL to a RyuuGames post (e.g. https://www.ryuugames.com/xxx/)
     */
    suspend fun scrapeGame(url: String): ScrapeResult {
        return try {
            val doc = fetchDocument(url)
            val title = extractTitle(doc) ?: return ScrapeResult.Error("Could not extract title")
            val description = extractDescription(doc)

            ScrapeResult.Success(
                game = Game(
                    title = title,
                    description = description,
                    developer = extractInfoValue(doc, "Developer"),
                    engine = extractEngine(listOfNotNull(title, description).joinToString("\n")),
                    version = extractVersion(description),
                    coverUrl = extractCoverUrl(doc, url),
                    f95Url = null,
                    f95Rating = null,
                    inLibrary = false,
                    sourceType = SourceType.RYUU_GAMES,
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

    /**
     * Fetch just the cover image for a post URL — the lazy cover-enrichment
     * path used by the browser grid (search cards already carry data-img-url
     * thumbnails, but this covers results without one).
     */
    suspend fun fetchCover(url: String): String? {
        return try {
            extractCoverUrl(fetchDocument(url), url)
        } catch (e: Exception) {
            null
        }
    }

    // ── Private helpers ────────────────────────────────────

    private suspend fun fetchSearchPage(url: String, query: String): Document {
        return try {
            postSearchPage(url, query)
        } catch (e: Exception) {
            // POST is the canonical search path, but if it fails (blocked,
            // timeout) a plain GET may still pass — retry once on the first
            // page only; later pages rethrow.
            if (url != BASE_URL) throw e
            getSearchPage(query)
        }
    }

    private suspend fun postSearchPage(url: String, query: String): Document {
        // Search is only served over POST. Out-of-range /page/N/ returns 404
        // with a card-less age-gate shell, so 404 is benign (pagination
        // exhaustion); 403/429 and Cloudflare challenge shells are real blocks.
        val response = Jsoup.connect(url)
            .userAgent(USER_AGENT)
            .timeout(TIMEOUT_MS.toInt())
            .followRedirects(true)
            .ignoreHttpErrors(true)
            .method(Connection.Method.POST)
            .data("s", query)
            .execute()
        return checkNotBlocked(response)
    }

    private suspend fun getSearchPage(query: String): Document {
        val response = Jsoup.connect("$BASE_URL/?s=${URLEncoder.encode(query, "UTF-8")}")
            .userAgent(USER_AGENT)
            .timeout(TIMEOUT_MS.toInt())
            .followRedirects(true)
            .ignoreHttpErrors(true)
            .execute()
        return checkNotBlocked(response)
    }

    /** Throws [ScrapeBlockedException] for 403/429 or Cloudflare challenge shells. */
    private fun checkNotBlocked(response: Connection.Response): Document {
        val doc = response.parse()
        if (response.statusCode() == 403 || response.statusCode() == 429 || isCloudflareChallenge(doc)) {
            throw ScrapeBlockedException(
                "Ryuugames blocked the request (Cloudflare). Open the site in a browser and try again.",
            )
        }
        return doc
    }

    private fun isCloudflareChallenge(doc: Document): Boolean {
        val text = doc.title() + " " + doc.body().text()
        return text.contains("cloudflare", ignoreCase = true) ||
            text.contains("just a moment", ignoreCase = true) ||
            text.contains("captcha", ignoreCase = true) ||
            text.contains("access denied", ignoreCase = true)
    }

    private suspend fun fetchDocument(url: String): Document {
        return Jsoup.connect(url)
            .userAgent(USER_AGENT)
            .timeout(TIMEOUT_MS.toInt())
            .followRedirects(true)
            .get()
    }

    private fun parseCards(doc: Document): List<SearchResult> {
        // Primary: the ThemeNova td_module_1 card. Fall back to generic
        // WordPress article containers if the theme's markup drifts.
        val cards = doc.select("div.td_module_1.td_module_wrap")
            .takeIf { it.isNotEmpty() }
            ?: doc.select("article")

        return cards.mapNotNull { card ->
            val link = card.selectFirst("h3.entry-title a[href]")
                ?: card.selectFirst("h2.entry-title a[href]")
                ?: card.selectFirst(".td-module-thumb a[href]")
                ?: card.selectFirst("a[href^=\"https://www.ryuugames.com/\"]")
                ?: return@mapNotNull null
            val href = link.attr("href")
            if (href.isBlank()) return@mapNotNull null

            val thumbEl = card.selectFirst("img.entry-thumb, img")
            SearchResult(
                title = link.text().trim()
                    .ifEmpty { link.attr("title").trim() }
                    .ifEmpty { "Unknown" },
                // Strip ?_rt=...&_rt_nonce=... tracking params from result hrefs.
                url = stripTrackingParams(normalizeUrl(href, BASE_URL)),
                // src is a base64 placeholder on td cards — data-img-url only;
                // fall back to a real src when the fallback markup lacks it.
                thumbnailUrl = thumbEl?.attr("data-img-url")?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: thumbEl?.attr("src")?.trim()
                        ?.takeIf { it.isNotBlank() && !it.startsWith("data:") },
            )
        }.distinctBy { it.url }
    }

    private fun extractTitle(doc: Document): String? {
        return doc.selectFirst(".td-post-title h1.entry-title")?.text()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: doc.selectFirst("meta[property=\"og:title\"]")?.attr("content")?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: doc.title().trim().takeIf { it.isNotEmpty() }
    }

    private fun extractDescription(doc: Document): String? {
        val body = doc.selectFirst(".td-post-content")?.text()?.trim()?.take(1000)
        val downloads = doc.select("a.ryuu-sl-vip-btn[href]")
            .mapNotNull { it.attr("href").trim().takeIf { href -> href.isNotBlank() } }
            .distinct()
        return when {
            body.isNullOrBlank() && downloads.isEmpty() -> null
            downloads.isEmpty() -> body
            else -> listOfNotNull(body?.takeIf { it.isNotBlank() })
                .plus(downloads.map { "Download: $it" })
                .joinToString("\n")
        }
    }

    private fun extractEngine(text: String?): GameEngine? {
        if (text.isNullOrEmpty()) return null

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

    private fun extractVersion(description: String?): String? {
        val text = description ?: return null

        // Common patterns: "Version: 1.0", "v1.0.0", "Current version 0.5a"
        val patterns = listOf(
            Regex("""[Vv]ersion[:\s]*([\d.]+[a-zA-Z]*)"""),
            Regex("""[Cc]urrent\s+version[:\s]*([\d.]+[a-zA-Z]*)"""),
            Regex("""\b(v[\d]+\.[\d]+[\w.]*)\b"""),
        )

        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        return null
    }

    private fun extractCoverUrl(doc: Document, pageUrl: String): String? {
        val selectors = listOf(
            // og:image is the canonical cover; .td-post-content can inline
            // logos/screenshots that should not win over it.
            "meta[property=\"og:image\"]",
            "meta[property=\"twitter:image\"]",
            // Fall back to the first post-body image — prefer data-img-url,
            // then content, then src (base64 placeholders are filtered below).
            ".td-post-content img",
        )

        for (selector in selectors) {
            val el = doc.selectFirst(selector) ?: continue
            val src = el.attr("data-img-url")
                .ifBlank { el.attr("content") }
                .ifBlank { el.attr("src") }
            if (src.isNotBlank() && !src.startsWith("data:image")) {
                return normalizeUrl(src, pageUrl)
            }
        }

        return null
    }

    private fun extractTags(doc: Document): List<Tag> {
        val tagNames = doc.select("ul.td-post-small-box li a")
            .mapNotNull { it.text().trim().takeIf { name -> name.isNotBlank() } }
            .distinct()
            .take(MAX_TAGS)
        return tagNames.map { Tag(name = it) }
    }

    /**
     * Extracts a "Label : value" line from the post body.
     *
     * Info blocks render label/value pairs separated by <br> or block
     * boundaries, which Jsoup's text() collapses into spaces. The body's HTML
     * is flattened to one pair per line instead, and the label is matched
     * against a single line.
     */
    private fun extractInfoValue(doc: Document, label: String): String? {
        val content = doc.selectFirst(".td-post-content") ?: return null
        val lines = flattenHtmlToLines(content.html())
        val pattern = Regex("""(?i)\s*${Regex.escape(label)}\s*[:：]\s*(.*)$""")
        return lines.firstNotNullOfOrNull { line ->
            pattern.find(line)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
        }
    }

    /** Converts body HTML to text with one label/value pair per line. */
    private fun flattenHtmlToLines(html: String): List<String> {
        val asLines = html
            .replace(Regex("""<br\s*/?>"""), "\n")
            .replace(Regex("""</?(?:p|div|li|tr|td|th|h[1-6])(?:\s[^>]*)?>"""), "\n")
            .replace(Regex("""<[^>]+>"""), "")
        return asLines.lines()
            .map { Parser.unescapeEntities(it, false).trim() }
            .filter { it.isNotBlank() }
    }

    /**
     * Normalizes a possibly-relative URL against [baseUrl].
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

    private fun stripTrackingParams(url: String): String = url.substringBefore('?')
}
