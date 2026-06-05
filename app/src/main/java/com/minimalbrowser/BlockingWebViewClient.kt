package com.minimalbrowser

import android.graphics.Bitmap
import android.webkit.*

class BlockingWebViewClient(
    private val adBlocker: AdBlocker,
    private val onPageStarted: (String) -> Unit,
    private val onPageFinished: (String) -> Unit,
    private val onBlockedRequest: () -> Unit,
    private val customScript: () -> String,
    private val onHomePageRequest: () -> Unit
) : WebViewClient() {

    companion object {
        private val EMPTY_RESPONSE = WebResourceResponse("text/plain", "utf-8", null)
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val url = request.url.toString()
        if (adBlocker.isBlocked(url)) {
            onBlockedRequest()
            return EMPTY_RESPONSE
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        if (url.startsWith("prs://")) {
            onHomePageRequest()
            return true
        }
        return false
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onPageStarted(url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)

        // Inject cosmetic CSS
        val cosmeticScript = adBlocker.cosmeticScript()
        if (cosmeticScript.isNotBlank()) {
            view.evaluateJavascript(cosmeticScript, null)
        }

        // Inject custom user script
        val userScript = customScript()
        if (userScript.isNotBlank()) {
            view.evaluateJavascript(userScript, null)
        }

        onPageFinished(url)
    }
}
