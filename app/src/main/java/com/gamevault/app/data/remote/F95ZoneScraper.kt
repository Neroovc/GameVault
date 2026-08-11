package com.gamevault.app.data.remote

import com.gamevault.app.domain.model.Game
import com.gamevault.app.domain.model.GameEngine
import com.gamevault.app.domain.model.SourceType
import com.gamevault.app.domain.model.Tag
import org.jsoup.Connection
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicLong

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
        private const val MAX_TAGS = 12

        // Known creator/official platforms for strict dev-link extraction.
        private val DEV_HOST_FRAGMENTS = listOf(
            "patreon", "discord", "telegram", "t.me", "youtube", "youtu.be",
            "x.com", "twitter", "reddit", "steam", "steampowered", "itch.io",
            "gog.com", "play.google.com", "github", "ko-fi", "buymeacoffee",
            "subscribestar",
        )

        // Brave throttling: searches spaced >= 2s apart, shared across all
        // instances so nothing ever bursts the search endpoint. Tunable at
        // runtime via [applyPace] so a user setting can override the defaults.
        @Volatile var braveMinIntervalMs: Long = 2_000L
        private val lastBraveSearchTime = AtomicLong(0L)

        // f95zone.to page-fetch taming: at most 2 concurrent fetches, spaced
        // >= 1.2s apart, so the grid's ~20-fetch cover burst stays gentle.
        @Volatile var pageFetchMinIntervalMs: Long = 1_200L
        @Volatile var maxConcurrentPageFetches: Int = 2
        @Volatile private var requestLimiter: Semaphore = Semaphore(maxConcurrentPageFetches)

        /**
         * Runtime-tunable pace controls so users can trade speed for gentleness
         * when F95Zone rate-limits the app. Rebuilds the limiter with the new
         * concurrency; a benign race is acceptable — the next fetch reads the
         * fresh values and swaps in the new semaphore.
         */
        fun applyPace(minPageIntervalMs: Long, maxConcurrent: Int, minBraveIntervalMs: Long) {
            pageFetchMinIntervalMs = minPageIntervalMs
            maxConcurrentPageFetches = maxConcurrent
            braveMinIntervalMs = minBraveIntervalMs
            requestLimiter = Semaphore(maxConcurrent)
        }
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
                    changelog = extractChangelog(doc),
                    devLinks = extractDevLinks(doc),
                    downloadLinks = extractDownloadLinks(doc),
                    coverUrl = extractCoverUrl(doc),
                    f95Url = url,
                    f95Rating = extractRating(doc),
                    inLibrary = false,
                    sourceType = SourceType.F95ZONE,
                    sourceUrl = url,
                    tags = extractTags(doc),
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
     *
     * F95Zone's own search endpoint returns HTTP 403 for non-browser clients
     * (verified against live HTML), and Bing/DuckDuckGo are blocked or useless
     * (Turnstile CAPTCHA / attachment noise). Brave Search works and serves
     * real thread results, so we query it with a `site:` restriction.
     *
     * Verified result structure (search.brave.com/search):
     *   block     = div.snippet[data-pos]
     *   url       = a[href*="f95zone.to/threads/"]  (direct thread URL, no redirect wrapper)
     *   title     = div.title.search-snippet-title
     *   snippet   = .snippet-description
     *
     * Failures are NOT silent: Brave challenge shells (200 with no snippets)
     * and HTTP blocks (429/403) throw [ScrapeBlockedException], which the
     * adapters surface as a visible error. Only a clean 200 with genuinely no
     * results returns an empty list.
     *
     * @param cookie Kept in the signature for API stability (used by scrapeGame);
     *               Brave search works without it.
     */
    suspend fun search(query: String, cookie: String? = null): List<SearchResult> {
        return try {
            val searchUrl = "https://search.brave.com/search?q=" +
                URLEncoder.encode("site:f95zone.to $query", "UTF-8")
            val doc = fetchBraveSearch(searchUrl, cookie).parse()

            val blocks = doc.select("div.snippet")
            val results = if (blocks.isNotEmpty()) {
                blocks.mapNotNull { block ->
                    val link = block.selectFirst("a[href*=\"f95zone.to/threads/\"]")
                        ?: return@mapNotNull null
                    SearchResult(
                        title = extractBraveTitle(block, link),
                        url = link.attr("href"),
                        snippet = block.selectFirst(".snippet-description")?.text(),
                    )
                }
            } else {
                // Fallback: Brave may change its markup — pick thread links anywhere.
                doc.select("a[href*=\"f95zone.to/threads/\"]").mapNotNull { link ->
                    SearchResult(
                        title = extractBraveTitle(null, link),
                        url = link.attr("href"),
                    )
                }
            }

            // A CAPTCHA/challenge shell carries no snippets and no thread links.
            if (results.isEmpty() && isSearchBlocked(doc)) {
                throw ScrapeBlockedException(
                    "Search engine blocked the request (rate limit). Try again in a minute.",
                )
            }
            results.take(20)
        } catch (e: ScrapeBlockedException) {
            throw e
        } catch (e: HttpStatusException) {
            throw ScrapeBlockedException(
                if (e.statusCode == 403 || e.statusCode == 429) {
                    "Search engine blocked the request (rate limit). Try again in a minute."
                } else {
                    "Search engine returned HTTP ${e.statusCode}. Try again in a minute."
                },
            )
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            throw ScrapeBlockedException("Search failed: $msg. Try again in a minute.")
        }
    }

    /**
     * Fetch the latest threads from F95Zone's "What's new" page.
     *
     * Uses the same f95zone.to page-fetch path as [scrapeGame] (shared
     * concurrency limit and applyPace-tunable interval). A login/Cloudflare
     * challenge or HTTP block throws [ScrapeBlockedException] instead of
     * returning an empty list. Returns up to 12 results.
     */
    suspend fun fetchRecent(): List<SearchResult> {
        return try {
            val doc = fetchDocument("https://f95zone.to/whats-new/", null)
            val results = doc.select(".structItem--thread").mapNotNull { row ->
                val link = row.selectFirst("a.structItem-title") ?: return@mapNotNull null
                val href = link.attr("href")
                if (href.isBlank()) return@mapNotNull null
                SearchResult(
                    title = link.text().trim().ifEmpty { "Unknown" },
                    url = normalizeUrl(href),
                    snippet = row.selectFirst(".structItem-main")?.text()?.take(120),
                )
            }
            // A challenge shell carries no thread rows — surface it as a block.
            if (results.isEmpty() && isSearchBlocked(doc)) {
                throw ScrapeBlockedException(
                    "F95Zone blocked the recent list (login or challenge). Try again later.",
                )
            }
            results.take(12)
        } catch (e: ScrapeBlockedException) {
            throw e
        } catch (e: HttpStatusException) {
            throw ScrapeBlockedException(
                if (e.statusCode == 403 || e.statusCode == 429) {
                    "F95Zone blocked the recent list (login or challenge). Try again later."
                } else {
                    "F95Zone returned HTTP ${e.statusCode} for the recent list. Try again later."
                },
            )
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            throw ScrapeBlockedException("Failed to fetch F95Zone recent list: $msg. Try again later.")
        }
    }

    /**
     * Fetch just the cover image for a thread URL.
     * Thread pages are fetchable by Jsoup (proven in production), so this is
     * the lazy cover-enrichment path used by the browser grid.
     */
    suspend fun fetchCover(url: String, cookie: String? = null): String? {
        return try {
            val doc = fetchDocument(url, cookie)
            extractCoverUrl(doc)
        } catch (e: HttpStatusException) {
            // 403/429 mean the site is actively blocking us — surface that so
            // the grid stops hammering instead of silently retrying covers.
            if (e.statusCode == 403 || e.statusCode == 429) {
                throw ScrapeBlockedException("Rate limited by F95Zone. Try again in a minute.")
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    // ── Private helpers ────────────────────────────────────

    private fun extractBraveTitle(block: Element?, link: Element): String {
        val raw = block?.selectFirst("div.title.search-snippet-title")?.text()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: link.text().trim().takeIf { it.isNotEmpty() }
            ?: "Unknown"
        // Brave titles often carry a trailing site suffix, e.g. "Game Name - F95zone".
        return raw.replace(Regex("""(?i)(\s*[-–—|]\s*)?F95zone\s*$"""), "")
            .trim()
            .ifEmpty { "Unknown" }
    }

    /**
     * Fetches a Brave search page under the shared search throttle (at most one
     * request per [braveMinIntervalMs] across all instances). HTTP errors are
     * NOT ignored, so 429/403 surface as [HttpStatusException] instead of
     * parsing a block page as an empty result. Runs on the IO dispatcher, so
     * the sleep is acceptable.
     */
    private suspend fun fetchBraveSearch(url: String, cookie: String?): Connection.Response {
        val now = System.currentTimeMillis()
        val waitMs = braveMinIntervalMs - (now - lastBraveSearchTime.get())
        if (waitMs > 0) Thread.sleep(waitMs)
        lastBraveSearchTime.updateAndGet { prev -> maxOf(prev, now + waitMs.coerceAtLeast(0)) }

        val conn = Jsoup.connect(url)
            .userAgent(USER_AGENT)
            .timeout(TIMEOUT_MS.toInt())
            .followRedirects(true)

        if (cookie != null) {
            conn.cookie("xf_session", cookie)
        }

        return conn.execute()
    }

    /** True when [doc] is a Brave CAPTCHA/challenge shell (200 but gated). */
    private fun isSearchBlocked(doc: Document): Boolean {
        val text = doc.title() + " " + doc.body().text()
        return text.contains("captcha", ignoreCase = true) ||
            text.contains("challenge", ignoreCase = true)
    }

    private suspend fun fetchDocument(url: String, cookie: String?): Document {
        val conn = Jsoup.connect(url)
            .userAgent(USER_AGENT)
            .timeout(TIMEOUT_MS.toInt())
            .followRedirects(true)

        if (cookie != null) {
            conn.cookie("xf_session", cookie)
        }

        // Cap concurrency and space out f95zone.to page fetches (covers,
        // details): the grid fires ~20 fetchCover calls at once, which trips
        // the site's 403. Runs on the IO dispatcher, so the sleep is fine.
        requestLimiter.acquire()
        try {
            Thread.sleep(pageFetchMinIntervalMs)
            return conn.get()
        } finally {
            requestLimiter.release()
        }
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
        // 1) Prefer the real developer from the post's info table — F95Zone
        //    lists it under "Developers:" (sometimes "Studio:", "Created by").
        // 2) Fall back to scanning the first post body for a developer
        //    mention. 3) Last resort: the thread poster (article[data-author])
        //    — on F95Zone that is usually the re-publisher, not the creator.
        return extractInfoValue(
            doc,
            "Developers", "Developer", "Studio", "Created by", "Made by", "Developer(s)",
        )
            ?: extractDeveloperFromBody(doc)
            ?: doc.selectFirst("article[data-author]")?.attr("data-author")
    }

    private val genericDeveloperWords = setOf("unknown", "anonymous", "various", "n/a")

    /** Scans the first post body for a "Developer/Created by/Made by" mention. */
    private fun extractDeveloperFromBody(doc: Document): String? {
        val body = doc.selectFirst("article.message-body .bbWrapper")?.text() ?: return null
        val match = Regex(
            """(?i)(developer|created by|made by)\s*:?\s*([A-Za-z0-9][A-Za-z0-9 ._'-]{2,60})""",
        ).find(body) ?: return null
        val candidate = match.groupValues[2].trim()
        return candidate.takeIf { it.lowercase() !in genericDeveloperWords }
    }

    /** Reads a "Label: value" pair from the thread's info table (dl.pairsJustified). */
    private fun extractInfoValue(doc: Document, vararg labels: String): String? {
        val wanted = labels.map { it.lowercase() }
        for (row in doc.select("dl.pairsJustified")) {
            val label = row.selectFirst("dt")?.text()?.trim()?.lowercase() ?: continue
            if (label in wanted) {
                val value = row.selectFirst("dd")?.text()?.trim()
                if (!value.isNullOrBlank()) return value
            }
        }
        return null
    }

    /**
     * Extracts the changelog section from the first post body (markers:
     * "Changelog" / "Change Log"), up to 600 chars. Null when absent.
     */
    private fun extractChangelog(doc: Document): String? {
        val body = doc.selectFirst("article.message-body .bbWrapper") ?: return null
        val lines = flattenHtmlToLines(body.html())
        val marker = Regex("""(?i)\b(?:changelog|change\s*log)\b""")
        val idx = lines.indexOfFirst { marker.containsMatchIn(it) }
        if (idx < 0) return null

        // Content after the marker on the same line, plus the lines that follow.
        val markerMatch = marker.find(lines[idx]) ?: return null
        val rest = lines[idx].substring(markerMatch.range.last + 1).trim()
        val out = StringBuilder()
        if (rest.isNotEmpty()) {
            out.append(rest).append('\n')
        }
        for (i in idx + 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue
            out.append(line).append('\n')
            if (out.length >= 600) break
        }
        return out.toString().trim().takeIf { it.isNotBlank() }?.take(600)
    }

    /**
     * Creator/official links from the first post (dev site, Patreon, Discord,
     * ...). STRICT: only absolute http(s) links whose host contains a known
     * creator/platform fragment. Download hosts are excluded — they belong to
     * [extractDownloadLinks]. Max 4, deduplicated.
     */
    private fun extractDevLinks(doc: Document): List<String> {
        val body = doc.selectFirst("article.message-body .bbWrapper")
            ?: return emptyList()
        return body.select("a[href]")
            .mapNotNull { it.attr("href").trim().takeIf { href -> href.isNotBlank() } }
            .filter { href ->
                val lower = href.lowercase()
                (lower.startsWith("https://") || lower.startsWith("http://")) &&
                    DEV_HOST_FRAGMENTS.any { lower.contains(it) } &&
                    !isDownloadHost(href)
            }
            .distinct()
            .take(4)
    }

    /**
     * Download links from the first post body — known file hosts plus magnet
     * links. Raw URLs only (no shortener expansion — the user opens them in a
     * browser). Max 4, deduplicated.
     */
    private fun extractDownloadLinks(doc: Document): List<String> {
        val body = doc.selectFirst("article.message-body .bbWrapper")
            ?: return emptyList()
        return body.select("a[href]")
            .mapNotNull { it.attr("href").trim().takeIf { href -> href.isNotBlank() } }
            .filter { href ->
                href.startsWith("magnet:", ignoreCase = true) || isDownloadHost(href)
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

    /** Converts body HTML to text with one element per line. */
    private fun flattenHtmlToLines(html: String): List<String> {
        val asLines = html
            .replace(Regex("""<br\s*/?>"""), "\n")
            .replace(Regex("""</?(?:p|div|li|tr|td|th|h[1-6])(?:\s[^>]*)?>"""), "\n")
            .replace(Regex("""<[^>]+>"""), " ")
        return asLines.lines()
            .map { Parser.unescapeEntities(it, false).replace(Regex("""\s+"""), " ").trim() }
            .filter { it.isNotBlank() }
    }

    private fun extractEngine(doc: Document): GameEngine? {
        // F95Zone threads usually name the engine in the thread title
        // ("GameName [v0.1] [Ren'Py]") and/or an info row ("Engine: Ren'Py"),
        // so search those alongside the first post body.
        val title = extractTitle(doc)
        val body = doc.selectFirst("article.message-body .bbWrapper")?.text()
        val infoRows = doc.select("dl.pairsJustified dt, dl.pairsJustified dd")
            .mapNotNull { it.text().trim().takeIf { text -> text.isNotEmpty() } }
            .joinToString(" ")

        val text = listOfNotNull(title, body, infoRows.takeIf { it.isNotEmpty() })
            .joinToString("\n")
            .trim()
        if (text.isEmpty()) return null

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
        // F95Zone now serves attachments from a CDN (attachments.f95zone.to).
        // Thumbnails live under a `/thumb/` path segment; the full-size image
        // is the same URL with that segment removed:
        //   https://attachments.f95zone.to/2019/02/thumb/255625_A_Good_time.jpg  (thumb)
        //   https://attachments.f95zone.to/2019/02/255625_A_Good_time.jpg        (full)
        //
        // Priority: post body image → wrapped image → any attachment image → meta tags.
        // We always strip `/thumb/` to prefer the full-size image.

        val selectors = listOf(
            // 1) First attachment image in the OP's post body
            "article.message-body .bbWrapper img[src*=\"attachments\"]",
            // 2) Any attachment image wrapped in a link
            "a[href*=\"attachments\"] img[src*=\"attachments\"]",
            // 3) Any remaining attachment image
            "img[src*=\"attachments\"]",
        )

        for (selector in selectors) {
            val el = doc.selectFirst(selector) ?: continue
            val src = el.attr("src")
            if (src.isNotBlank()) return normalizeUrl(stripThumbPath(src))
        }

        // Last resort: meta tags (og:image, twitter:image)
        for (metaSel in listOf("meta[property=\"og:image\"]", "meta[property=\"twitter:image\"]")) {
            val meta = doc.selectFirst(metaSel) ?: continue
            val content = meta.attr("content")
            if (content.isNotBlank()) return normalizeUrl(stripThumbPath(content))
        }

        return null
    }

    /** Removes the `/thumb/` path segment to upgrade a CDN thumbnail to the full-size URL. */
    private fun stripThumbPath(url: String): String = url.replace("/thumb/", "/")

    private fun extractRating(doc: Document): Float? {
        val ratingEl = doc.selectFirst(".rating-stars") ?: return null
        val style = ratingEl.attr("style")
        val widthMatch = Regex("width\\s*:\\s*(\\d+(\\.\\d+)?)%").find(style)
        return widthMatch?.groupValues?.get(1)?.toFloatOrNull()
            ?.let { (it / 100) * 5 }
            ?.let { (it * 2).toInt() / 2f }
    }

    private fun extractTags(doc: Document): List<Tag> {
        val tagNames = doc.select("a.tagItem, a[href*='/tags/']")
            .mapNotNull { it.text().trim().takeIf { name -> name.isNotBlank() && !name.contains("Create new tag") } }
            .distinct()
            .take(MAX_TAGS)
        return tagNames.map { Tag(name = it) }
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
// ScrapeResult moved to ScrapeResult.kt (shared with other scrapers).

data class SearchResult(
    val title: String,
    val url: String,
    val author: String? = null,
    val snippet: String? = null,
)
