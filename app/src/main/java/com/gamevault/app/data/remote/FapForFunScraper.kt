package com.gamevault.app.data.remote

import com.gamevault.app.domain.model.Game
import com.gamevault.app.domain.model.GameEngine
import com.gamevault.app.domain.model.SourceType
import com.gamevault.app.domain.model.Tag
import com.gamevault.app.domain.source.SearchResult
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
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
                    coverUrl = extractCoverUrl(doc, url),
                    f95Url = null,
                    f95Rating = null,
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
        val body = doc.selectFirst(".entry-content")?.text()?.trim()?.take(1000)
        val downloads = extractDownloadLinks(doc)
        val lines = buildList {
            body?.takeIf { it.isNotBlank() }?.let(::add)
            downloads.forEach { add("Download: $it") }
            if (downloads.isNotEmpty()) add("Password: $SITE_PASSWORD")
        }
        return lines.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    private fun extractDownloadLinks(doc: Document): List<String> {
        val content = doc.selectFirst(".entry-content") ?: return emptyList()
        val magnets = content.select("a[href^=\"magnet:\"]")
            .mapNotNull { it.attr("href").trim().takeIf { href -> href.isNotBlank() } }
            .distinct()
        if (magnets.isNotEmpty()) return magnets

        // Newer posts route downloads through shorteners that 403 to plain
        // clients — surface the URL so the user can open it in a browser.
        return content.select("a[href*=\"ouo.io\"], a[href*=\"exe.io\"]")
            .mapNotNull { it.attr("href").trim().takeIf { href -> href.isNotBlank() } }
            .distinct()
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
        val selectors = listOf(
            // First image in the post body (wp-image-* screenshots).
            ".entry-content img",
            "meta[property=\"og:image\"]",
        )

        for (selector in selectors) {
            val el = doc.selectFirst(selector) ?: continue
            val src = el.attr("content").ifBlank { el.attr("src") }
            if (src.isNotBlank() && !src.startsWith("data:image")) {
                return normalizeUrl(src, pageUrl)
            }
        }

        return null
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
