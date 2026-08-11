package com.gamevault.app.data.remote

import com.gamevault.app.domain.model.Game
import com.gamevault.app.domain.model.GameEngine
import com.gamevault.app.domain.model.SourceType
import com.gamevault.app.domain.model.Tag
import com.gamevault.app.domain.source.SearchResult
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.net.URI
import java.net.URLEncoder

/**
 * Scrapes game metadata from FapForFun (https://fapforfun.net).
 *
 * Plain WordPress (OceanWP theme) with no Cloudflare or age gate — a GET
 * works everywhere. Search cards carry no thumbnail (articles use the
 * no-featured-image template), so thumbnailUrl is left null and the cover
 * is resolved lazily via [fetchCover].
 *
 * Older posts host direct magnet links; newer posts route downloads through
 * shorteners (ouo.io / exe.io) that 403 to plain clients, so only the
 * shortener URL is surfaced for the user to open in a browser. The unzip
 * password is the site-wide "fapforfun".
 */
class FapForFunScraper {

    companion object {
        // Realistic Android Chrome UA to avoid blocks
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/125.0.6422.165 Mobile Safari/537.36"
        private const val TIMEOUT_MS = 30_000L
        private const val BASE_URL = "https://fapforfun.net"
        private const val MAX_PAGES = 5
        private const val MAX_TAGS = 12
        private const val SITE_PASSWORD = "fapforfun"
    }

    /**
     * Search FapForFun for games matching a query.
     *
     * Returns the domain [SearchResult] directly (the scraper layer is the
     * only consumer of search results, so no local mirror type is needed).
     */
    suspend fun search(query: String): List<SearchResult> {
        return try {
            val results = mutableListOf<SearchResult>()
            var page = 1
            while (page <= MAX_PAGES) {
                // ?s=QUERY&paged=N is the canonical WordPress search pagination
                // (verified live; the /page/N/?s=QUERY form also works).
                val searchUrl = "$BASE_URL/?s=${URLEncoder.encode(query, "UTF-8")}&paged=$page"
                val cards = parseCards(fetchSearchPage(searchUrl))
                if (cards.isEmpty()) break
                results += cards
                page++
            }
            results
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Scrape a game info page by URL.
     * @param url Full URL to a FapForFun post (e.g. https://fapforfun.net/archives/40397)
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
                    developer = extractDeveloper(doc),
                    engine = extractEngine(description),
                    version = null,
                    changelog = null,
                    devLinks = extractDevLinks(doc),
                    downloadLinks = extractDownloadLinks(doc),
                    coverUrl = extractCoverUrl(doc, url),
                    f95Url = null,
                    f95Rating = null,
                    inLibrary = false,
                    sourceType = SourceType.FAP_FOR_FUN,
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
     * path used by the browser grid (search cards carry no thumbnails).
     */
    suspend fun fetchCover(url: String): String? {
        return try {
            extractCoverUrl(fetchDocument(url), url)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Fetch the latest posts from the FapForFun home page.
     *
     * Non-2xx answers surface as [ScrapeBlockedException]. Reuses the proven
     * search-card selectors, with a TDMag-style (.td_module) fallback in case
     * the home template differs from search results. Returns up to 12 results.
     */
    suspend fun fetchRecent(): List<SearchResult> {
        return try {
            val response = Jsoup.connect(BASE_URL)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS.toInt())
                .followRedirects(true)
                .ignoreHttpErrors(true)
                .execute()
            val status = response.statusCode()
            if (status !in 200..299) {
                throw ScrapeBlockedException(
                    "FapForFun blocked the recent list (HTTP $status). Try again later.",
                )
            }
            parseRecentCards(response.parse()).take(12)
        } catch (e: ScrapeBlockedException) {
            throw e
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            throw ScrapeBlockedException("Failed to fetch FapForFun recent list: $msg")
        }
    }

    /**
     * Recent-list cards: the proven search selectors first, then a
     * best-effort TDMag-style fallback (.td_module rows).
     */
    private fun parseRecentCards(doc: Document): List<SearchResult> {
        val cards = parseCards(doc)
        if (cards.isNotEmpty()) return cards
        return doc.select(".td_module").mapNotNull { card ->
            val link = card.selectFirst(".td-module-title a") ?: return@mapNotNull null
            val href = link.attr("href")
            if (href.isBlank()) return@mapNotNull null
            SearchResult(
                title = link.text().trim().ifEmpty { "Unknown" },
                url = normalizeUrl(href, BASE_URL),
                thumbnailUrl = null,
            )
        }
    }

    // ── Private helpers ────────────────────────────────────

    private suspend fun fetchSearchPage(url: String): Document {
        // Out-of-range ?paged=N returns HTTP 404 with a no-results shell; the
        // response is parsed anyway so pagination stops cleanly on empty pages.
        return Jsoup.connect(url)
            .userAgent(USER_AGENT)
            .timeout(TIMEOUT_MS.toInt())
            .followRedirects(true)
            .ignoreHttpErrors(true)
            .get()
    }

    private suspend fun fetchDocument(url: String): Document {
        return Jsoup.connect(url)
            .userAgent(USER_AGENT)
            .timeout(TIMEOUT_MS.toInt())
            .followRedirects(true)
            .get()
    }

    private fun parseCards(doc: Document): List<SearchResult> {
        // Category pages render h2.blog-entry-title, search pages render
        // h2.search-entry-title — both carry the entry-title class, so the
        // combined selector covers both templates.
        return doc.select("article.entry h2.blog-entry-title a, article.entry h2.entry-title a")
            .mapNotNull { link ->
                val href = link.attr("href")
                if (href.isBlank()) return@mapNotNull null
                SearchResult(
                    title = link.text().trim().ifEmpty { "Unknown" },
                    url = normalizeUrl(href, BASE_URL),
                    // Cards use the no-featured-image template — the detail
                    // page (via fetchCover) provides the cover.
                    thumbnailUrl = null,
                )
            }
    }

    private fun extractTitle(doc: Document): String? {
        // The single-post-title class is stable; the element is an h1 on some
        // posts and an h2 on others, so match on class only.
        return doc.selectFirst(".single-post-title.entry-title")?.text()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: doc.selectFirst("meta[property=\"og:title\"]")?.attr("content")?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: doc.title().substringBefore(" – ").trim().takeIf { it.isNotEmpty() }
    }

    private fun extractDescription(doc: Document): String? {
        return doc.selectFirst(".entry-content")?.text()?.trim()?.take(1000)
    }

    /**
     * Download links from the post body: magnet links, known file hosts, and
     * (for newer posts) shortener URLs (ouo.io / exe.io) that 403 to plain
     * clients — surfaced raw so the user can open them in a browser.
     * Max 4, deduplicated.
     */
    private fun extractDownloadLinks(doc: Document): List<String> {
        val content = doc.selectFirst(".entry-content") ?: return emptyList()
        val magnets = content.select("a[href^=\"magnet:\"]")
            .mapNotNull { it.attr("href").trim().takeIf { href -> href.isNotBlank() } }
        val fileHosts = content.select("a[href]")
            .mapNotNull { it.attr("href").trim().takeIf { href -> href.isNotBlank() } }
            .filter { isDownloadHost(it) }
        val shorteners = content.select("a[href*=\"ouo.io\"], a[href*=\"exe.io\"]")
            .mapNotNull { it.attr("href").trim().takeIf { href -> href.isNotBlank() } }
        return (magnets + fileHosts + shorteners).distinct().take(4)
    }

    /**
     * External links from the post body (dev site, Patreon, Discord, ...).
     * Absolute http(s) links only; same-site links, download/paylink
     * patterns (magnets, ouo.io / exe.io shorteners) and known download
     * hosts (those go to [extractDownloadLinks]) are filtered out.
     * Max 4, deduplicated.
     */
    private fun extractDevLinks(doc: Document): List<String> {
        val content = doc.selectFirst(".entry-content") ?: return emptyList()
        return content.select("a[href]")
            .mapNotNull { it.attr("href").trim().takeIf { href -> href.isNotBlank() } }
            .filter { href ->
                val lower = href.lowercase()
                (lower.startsWith("https://") || lower.startsWith("http://")) &&
                    !lower.contains("fapforfun.net") &&
                    !lower.contains("ouo.io") &&
                    !lower.contains("exe.io") &&
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

    private fun extractDeveloper(doc: Document): String? {
        // Doujin posts name the circle ("Circle:"), anime posts the studio
        // ("Studio:") — both are the closest thing to a developer.
        return listOf("Circle", "Studio").firstNotNullOfOrNull { label ->
            extractInfoValue(doc, label)
        }
    }

    private fun extractEngine(description: String?): GameEngine? {
        val text = description ?: return null
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
        val metaSelectors = listOf(
            // Prefer the full-size og:image / twitter:image over body images,
            // which WordPress serves as -300x200-style resized variants.
            "meta[property=\"og:image\"]",
            "meta[property=\"twitter:image\"]",
            "meta[name=\"twitter:image\"]",
        )

        for (selector in metaSelectors) {
            val el = doc.selectFirst(selector) ?: continue
            val src = el.attr("content").ifBlank { el.attr("src") }
            if (src.isNotBlank() && !src.startsWith("data:image")) {
                return normalizeUrl(stripWordPressResize(src), pageUrl)
            }
        }

        // FAP posts open with screenshots hosted externally (imagetwist)
        // BEFORE the real cover, so the first body <img> picks the wrong
        // image. Prefer the first `.entry-content img` served from the site's
        // own domain, where the cover lives (fapforfun.net/wp-content/...).
        val contentImages = doc.select(".entry-content img")
        val ownDomainSrc = contentImages.firstNotNullOfOrNull { img ->
            resolveImageSrc(img).takeIf { it.contains("fapforfun.net") }
        }
        if (ownDomainSrc != null) {
            return normalizeUrl(stripWordPressResize(ownDomainSrc), pageUrl)
        }

        // Last resort: first image in the post body (as before).
        for (img in contentImages) {
            val src = resolveImageSrc(img)
            if (src.isNotBlank() && !src.startsWith("data:image")) {
                return normalizeUrl(stripWordPressResize(src), pageUrl)
            }
        }

        return null
    }

    /** Resolves an <img> to its real source, honoring WordPress lazy-load
     *  attributes (data-src / data-lazy-src) when src is a placeholder. */
    private fun resolveImageSrc(img: Element): String {
        val src = img.attr("src")
        return if (src.isBlank() || src.startsWith("data:image")) {
            img.attr("data-src").ifBlank { img.attr("data-lazy-src") }
        } else {
            src
        }
    }

    /**
     * Upgrades a WordPress resized variant to the full-size image URL, e.g.
     *   https://.../image-300x200.jpg -> https://.../image.jpg
     * Also strips the -scaled / -rotated suffixes WordPress 5.3+ adds.
     * URLs without such a suffix pass through unchanged.
     */
    private fun stripWordPressResize(url: String): String {
        val resizeSuffix = Regex("""-(?:\d{2,4}x\d{2,4}|scaled|rotated)(?=\.[a-zA-Z]{2,4}$)""")
        // Applied twice so "image-300x200-scaled.jpg" -> "image.jpg" in one pass.
        return resizeSuffix.replace(resizeSuffix.replace(url, ""), "")
    }

    private fun extractTags(doc: Document): List<Tag> {
        // Post metadata lists a comma-separated genre line — cheap tag source.
        val genres = extractInfoValue(doc, "Genre") ?: return emptyList()
        val tagNames = genres.split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(MAX_TAGS)
        return tagNames.map { Tag(name = it) }
    }

    /**
     * Extracts a "Label : value" line from the post body.
     *
     * Metadata pairs are rendered as "<strong>Label: </strong>value<br />",
     * which Jsoup's text() collapses into spaces. The body's HTML is flattened
     * to one pair per line instead, and the label is matched against a single
     * line.
     */
    private fun extractInfoValue(doc: Document, label: String): String? {
        val content = doc.selectFirst(".entry-content") ?: return null
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
}
