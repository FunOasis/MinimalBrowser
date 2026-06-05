package com.minimalbrowser

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.*
import android.util.Log

/**
 * WebViewClient that intercepts every resource request and blocks ads/trackers
 * using AdBlocker. Also injects cosmetic CSS after page load.
 */
class BlockingWebViewClient(
    private val adBlocker: AdBlocker,
    private val onPageStarted: (String) -> Unit,
    private val onPageFinished: (String) -> Unit,
    private val onBlockedRequest: () -> Unit
) : WebViewClient() {

    companion object {
        private const val TAG = "BlockingWebViewClient"
        private val EMPTY_RESPONSE = WebResourceResponse("text/plain", "utf-8", java.io.ByteArrayInputStream("".toByteArray()))
    }

    // ── Request interception ──────────────────────────────────────────────────

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val url = request.url.toString()

        if (adBlocker.isBlocked(url)) {
            Log.d(TAG, "BLOCKED: $url")
            onBlockedRequest()
            return EMPTY_RESPONSE
        }

        return super.shouldInterceptRequest(view, request)
    }

    // ── Page lifecycle ────────────────────────────────────────────────────────

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onPageStarted(url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)

        // Inject cosmetic element-hiding CSS
        val script = adBlocker.cosmeticScript()
        if (script.isNotBlank()) {
            view.evaluateJavascript(script, null)
        }

        onPageFinished(url)
    }

    // ── SSL / Error handling ──────────────────────────────────────────────────

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        // Only show error for main frame; subresource errors are often blocked ads
        if (request.isForMainFrame) {
            Log.e(TAG, "Page error ${error.errorCode}: ${error.description} for ${request.url}")
        }
    }
}
