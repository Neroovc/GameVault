package com.gamevault.app.data.remote

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.gamevault.app.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Bootstraps a Cloudflare clearance cookie by driving a HIDDEN WebView
 * through a JS challenge.
 *
 * Jsoup cannot execute JavaScript, so a Cloudflare challenge (403 / "Just a
 * moment") cannot be resolved by the scraper alone. This helper loads the
 * challenged page in a real Chromium [WebView] on the main thread — the
 * challenge's auto-script runs, Cloudflare sets `cf_clearance`, and we grab
 * the cookie from [CookieManager] and hand it to the scraper for a Jsoup
 * replay.
 *
 * UA binding: `cf_clearance` is bound to the browser's User-Agent (and IP),
 * so the caller MUST replay the Jsoup request with [CfCookie.userAgent] —
 * the exact UA string this WebView used. TTL is handled by the caller (the
 * scraper caches the cookie for a bounded time and re-runs the challenge
 * when stale).
 *
 * The WebView is never added to a view hierarchy and is always destroyed
 * before returning, so the user never sees it. The cookie VALUE is never
 * logged.
 */
class CloudflareCookieHelper(
    private val appContext: Context,
    @Suppress("unused") private val appSettings: AppSettings,
) {

    /** A clearance cookie plus the UA it was issued for. */
    data class CfCookie(val value: String, val userAgent: String)

    private companion object {
        const val POLL_INTERVAL_MS = 500L
        const val CF_CLEARANCE_PREFIX = "cf_clearance="
    }

    /**
     * Loads [url] in a hidden WebView and waits (polling every 500 ms, up to
     * [timeoutMs]) for a `cf_clearance` cookie to appear for the URL's host.
     *
     * Returns the cookie + the WebView's UA, or null on timeout/exception.
     * All WebView work happens on the main thread (creation, polling,
     * destruction); the coroutine resumes on the main dispatcher.
     */
    suspend fun obtainCookie(url: String, timeoutMs: Long = 25_000): CfCookie? {
        val cookieUrl = cookieHostUrl(url)
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val handler = Handler(Looper.getMainLooper())
                var webView: WebView? = null
                var pollRunnable: Runnable? = null
                var timeoutRunnable: Runnable? = null
                var settled = false

                // Single exit point: cancels timers, destroys the WebView on
                // the main thread and resumes the coroutine exactly once.
                fun finish(result: CfCookie?) {
                    if (settled) return
                    settled = true
                    pollRunnable?.let(handler::removeCallbacks)
                    timeoutRunnable?.let(handler::removeCallbacks)
                    webView?.destroy()
                    webView = null
                    continuation.resume(result)
                }

                continuation.invokeOnCancellation {
                    handler.post {
                        if (!settled) {
                            settled = true
                            pollRunnable?.let(handler::removeCallbacks)
                            timeoutRunnable?.let(handler::removeCallbacks)
                            webView?.destroy()
                            webView = null
                        }
                    }
                }

                try {
                    val wv = WebView(appContext)
                    webView = wv

                    val settings = wv.settings
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    // Keep the device-default UA: the WebView's own default
                    // is exactly what the challenge sees, so it is what the
                    // Jsoup replay must send.

                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(wv, true)

                    // Hard deadline for the whole challenge round-trip.
                    timeoutRunnable = Runnable { finish(null) }
                    handler.postDelayed(timeoutRunnable, timeoutMs)

                    // Polls CookieManager for the clearance cookie once the
                    // challenge page has finished loading. The !! is safe:
                    // pollRunnable is fully assigned before any callback can
                    // run (the Runnable only executes via postDelayed or
                    // onPageFinished, both of which happen after assignment).
                    pollRunnable = Runnable {
                        if (settled) return@Runnable
                        val cookie = runCatching {
                            cookieManager.getCookie(cookieUrl)
                        }.getOrNull()
                        val cfValue = cookie?.let { extractCfClearance(it) }
                        if (cfValue != null) {
                            finish(CfCookie(cfValue, settings.userAgentString))
                        } else {
                            handler.postDelayed(pollRunnable!!, POLL_INTERVAL_MS)
                        }
                    }

                    wv.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            handler.postDelayed(pollRunnable!!, POLL_INTERVAL_MS)
                        }
                    }

                    wv.loadUrl(url)
                } catch (e: Exception) {
                    finish(null)
                }
            }
        }
    }

    /**
     * Extracts the `cf_clearance` value from a CookieManager cookie string.
     * Chunks are separated by ';', so the value runs to the chunk's end.
     */
    private fun extractCfClearance(cookie: String): String? {
        for (chunk in cookie.split(";")) {
            val trimmed = chunk.trim()
            if (trimmed.startsWith(CF_CLEARANCE_PREFIX)) {
                return trimmed.substring(CF_CLEARANCE_PREFIX.length)
                    .takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    /** Reduces [url] to "scheme://authority" for CookieManager lookups. */
    private fun cookieHostUrl(url: String): String {
        val uri = Uri.parse(url)
        val authority = uri.authority
        return if (authority.isNullOrBlank()) {
            url
        } else {
            val scheme = uri.scheme?.takeIf { it.isNotEmpty() } ?: "https"
            "$scheme://$authority"
        }
    }
}
