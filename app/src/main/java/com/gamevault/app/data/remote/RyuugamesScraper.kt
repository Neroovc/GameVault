package com.gamevault.app.data.remote

import android.content.Context
import com.gamevault.app.data.settings.AppSettings
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
 *
 * Cloudflare clearance: when a fetch reports a challenge, the request is
 * replayed with a `cf_clearance` cookie obtained from a hidden WebView (see
 * [CloudflareCookieHelper]) using the WebView's own User-Agent — the cookie
 * is bound to that UA. The cookie is cached for 15 minutes and persisted via
 * [AppSettings]; the retry flow can never loop (one obtain per blocked fetch).
 */
class RyuugamesScraper(
    private val appContext: Context,
    private val appSettings: AppSettings,
) {

    private val cloudflareCookieHelper by lazy {
        CloudflareCookieHelper(appContext, appSettings)
    }

    // In-memory cache of the last Cloudflare clearance cookie. The value is
    // also persisted via AppSettings on every fresh obtain.
    private var cachedCfCookie: CloudflareCookieHelper.CfCookie? = null
    private var cookieFetchedAt: Long = 0

    companion object {
        // Realistic Android Chrome UA to avoid blocks
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/125.0.6422.165 Mobile Safari/537.36"
        private const val TIMEOUT_MS = 30_000L
        private const val BASE_URL = "https://www.ryuugames.com"
        private const val MAX_PAGES = 5
        private const val MAX_TAGS = 12
        private const val CF_COOKIE_TTL_MS = 15 * 60 * 1000L
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
                    changelog = extractChangelog(description),
                    devLinks = extractDevLinks(doc),
                    downloadLinks = extractDownloadLinks(doc),
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
        } catch (e: ScrapeBlockedException) {
            ScrapeResult.Error(e.message ?: "Ryuugames blocked the request")
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

    /**
     * Fetch the latest posts from the RyuuGames home page.
     *
     * Runs through the same Cloudflare check as search, so a challenged
     * response surfaces as [ScrapeBlockedException]. Reuses the proven
     * [parseCards] markup and returns up to 12 results.
     */
    suspend fun fetchRecent(): List<SearchResult> {
        return try {
            parseCards(fetchDocument(BASE_URL)).take(12)
        } catch (e: ScrapeBlockedException) {
            throw e
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            throw ScrapeBlockedException("Failed to fetch Ryuugames recent list: $msg")
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
        return withCfRetry(url) { cookie, userAgent ->
            postSearchPageRaw(url, query, cookie, userAgent)
        }
    }

    private fun postSearchPageRaw(
        url: String,
        query: String,
        cookie: String?,
        userAgent: String?,
    ): Document {
        // Search is only served over POST. Out-of-range /page/N/ returns 404
        // with a card-less age-gate shell, so 404 is benign (pagination
        // exhaustion); 403/429 and Cloudflare challenge shells are real blocks.
        val conn = Jsoup.connect(url)
            .userAgent(userAgent ?: USER_AGENT)
            .timeout(TIMEOUT_MS.toInt())
            .followRedirects(true)
            .ignoreHttpErrors(true)
            .method(Connection.Method.POST)
            .data("s", query)
        if (cookie != null) {
            conn.cookie("cf_clearance", cookie)
        }
        return checkNotBlocked(conn.execute())
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
                "Ryuugames blocked the request (Cloudflare challenge). Try again later or open the game page in your browser.",
            )
        }
        return doc
    }

    private fun isCloudflareChallenge(doc: Document): Boolean {
        val text = doc.title() + " " + doc.body().text()
        return text.contains("cloudflare", ignoreCase = true) ||
            text.contains("just a moment", ignoreCase = true) ||
            text.contains("attention required", ignoreCase = true) ||
            text.contains("cf-challenge", ignoreCase = true) ||
            text.contains("captcha", ignoreCase = true) ||
            text.contains("access denied", ignoreCase = true)
    }

    private suspend fun fetchDocument(url: String): Document {
        return fetchDocumentWithCf(url)
    }

    /**
     * Runs [docFetcher] without a cookie, then — ONLY when it reports a
     * Cloudflare block — replays it with a clearance cookie: a fresh
     * in-memory cache first, otherwise one hidden-WebView obtain. Arbitrary
     * exceptions propagate untouched (no retry). At most one WebView obtain
     * per blocked fetch, so the flow cannot loop.
     */
    private suspend fun withCfRetry(
        url: String,
        docFetcher: (cookie: String?, userAgent: String?) -> Document,
    ): Document {
        var originalBlock: ScrapeBlockedException
        try {
            return docFetcher(null, null)
        } catch (e: ScrapeBlockedException) {
            originalBlock = e
        }

        // Fresh cached cookie first — avoids the slow WebView path entirely.
        val now = System.currentTimeMillis()
        val cached = cachedCfCookie?.takeIf { now - cookieFetchedAt < CF_COOKIE_TTL_MS }
        if (cached != null) {
            try {
                return docFetcher(cached.value, cached.userAgent)
            } catch (e: ScrapeBlockedException) {
                // Cached cookie went stale server-side — refresh it below.
            }
        }

        // No cache, stale cache, or cached replay blocked: obtain exactly once.
        val cf = cloudflareCookieHelper.obtainCookie(url)
        if (cf == null) {
            throw originalBlock
        }
        appSettings.setRyuugamesCfCookie(cf.value)
        cachedCfCookie = cf
        cookieFetchedAt = System.currentTimeMillis()
        // May throw ScrapeBlockedException — propagates, no further retries.
        return docFetcher(cf.value, cf.userAgent)
    }

    private suspend fun fetchDocumentWithCf(url: String): Document {
        return withCfRetry(url) { cookie, userAgent ->
            fetchDocumentRaw(url, cookie, userAgent)
        }
    }

    private fun fetchDocumentRaw(url: String, cookie: String?, userAgent: String?): Document {
        // Same Cloudflare detection as the search path: a challenged detail
        // page would otherwise be scraped as a title of "Just a moment".
        val conn = Jsoup.connect(url)
            .userAgent(userAgent ?: USER_AGENT)
            .timeout(TIMEOUT_MS.toInt())
            .followRedirects(true)
            .ignoreHttpErrors(true)
        if (cookie != null) {
            conn.cookie("cf_clearance", cookie)
        }
        return checkNotBlocked(conn.execute())
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
        return doc.selectFirst(".td-post-content")?.text()?.trim()?.take(1000)
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

    /**
     * Best effort changelog: text after a "Changelog" marker in the post
     * body, up to 600 chars. Null when absent.
     */
    private fun extractChangelog(description: String?): String? {
        val text = description ?: return null
        val marker = Regex("""(?i)\b(?:changelog|change\s*log)\b""")
        val match = marker.find(text) ?: return null
        return text.substring(match.range.last + 1)
            .trim()
            .takeIf { it.isNotBlank() }
            ?.take(600)
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
     * Download links from the post body — the ryuu-sl-vip-btn download
     * buttons. Max 4, deduplicated.
     */
    private fun extractDownloadLinks(doc: Document): List<String> {
        val content = doc.selectFirst(".td-post-content") ?: return emptyList()
        return content.select("a.ryuu-sl-vip-btn[href]")
            .mapNotNull { it.attr("href").trim().takeIf { href -> href.isNotBlank() } }
            .distinct()
            .take(4)
    }

    /**
     * External links from the post body (dev site, Patreon, Discord, ...).
     * Absolute http(s) links only; same-site links, the download buttons
     * (ryuu-sl-vip-btn) and known download hosts are filtered out (those go
     * to [extractDownloadLinks]). Max 4, deduplicated.
     */
    private fun extractDevLinks(doc: Document): List<String> {
        val content = doc.selectFirst(".td-post-content") ?: return emptyList()
        return content.select("a[href]")
            .filterNot { it.hasClass("ryuu-sl-vip-btn") }
            .mapNotNull { it.attr("href").trim().takeIf { href -> href.isNotBlank() } }
            .filter { href ->
                val lower = href.lowercase()
                (lower.startsWith("https://") || lower.startsWith("http://")) &&
                    !lower.contains("ryuugames.com") &&
                    !isDownloadHost(href)
            }
            .distinct()
            .take(4)
    }

    /** True when [url] points at a known file host or download page. */
    private fun isDownloadHost(url: String): Boolean {
        val host = runCatching { URI(url).host }.getOrNull() ?: return false
        val lower = host.lowercase()
        return listOf(
            "mega.nz", "mediafire.com", "mfcdn.io", "pixeldrain.com", "1fichier.com",
            "gofile.io", "dropbox.com", "drive.google.com", "dl.free.fr",
        ).any { lower.contains(it) } ||
            (lower.contains("moddb.com") && url.contains("/download", ignoreCase = true))
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
