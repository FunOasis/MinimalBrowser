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

        // WebRTC leak prevention script
        // Disables RTCPeerConnection to prevent IP leaking via WebRTC
        private val WEBRTC_BLOCK_SCRIPT = """
            (function() {
                var noOp = function() {};
                if (window.RTCPeerConnection) {
                    window.RTCPeerConnection = function() {
                        return {
                            createOffer: noOp, createAnswer: noOp,
                            setLocalDescription: noOp, setRemoteDescription: noOp,
                            addIceCandidate: noOp, close: noOp,
                            addEventListener: noOp, removeEventListener: noOp
                        };
                    };
                }
                window.webkitRTCPeerConnection = window.RTCPeerConnection;
            })();
        """.trimIndent()
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

        // Block WebRTC leaks
        view.evaluateJavascript(WEBRTC_BLOCK_SCRIPT, null)

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
