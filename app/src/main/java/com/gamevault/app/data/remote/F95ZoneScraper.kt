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
                    coverUrl = extractCoverUrl(doc),
                    f95Url = url,
                    f95Rating = extractRating(doc),
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
