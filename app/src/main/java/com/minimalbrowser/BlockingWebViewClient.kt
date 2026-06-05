package com.minimalbrowser

import android.graphics.Bitmap
import android.webkit.*

class BlockingWebViewClient(
    private val adBlocker: AdBlocker,
    private val onPageStarted: (String) -> Unit,
    private val onPageFinished: (String) -> Unit,
    private val onBlockedRequest: () -> Unit,
    private val customScript: () -> String,
    private val onHomePageRequest: () -> Unit,
    private val passwordManager: PasswordManager,
    private val currentHost: () -> String
) : WebViewClient() {

    companion object {
        private val EMPTY_RESPONSE = WebResourceResponse("text/plain", "utf-8", null)

        private val WEBRTC_BLOCK_SCRIPT = """
            (function() {
                try {
                    if (window.RTCPeerConnection) {
                        var OrigRTC = window.RTCPeerConnection;
                        window.RTCPeerConnection = function(config) {
                            if (config && config.iceServers) config.iceServers = [];
                            return new OrigRTC(config);
                        };
                        window.RTCPeerConnection.prototype = OrigRTC.prototype;
                    }
                } catch(e) {}
            })();
        """.trimIndent()

        private val PASSWORD_DETECT_SCRIPT = """
            (function() {
                document.querySelectorAll('form').forEach(function(form) {
                    form.addEventListener('submit', function() {
                        var user = '';
                        var pass = '';
                        form.querySelectorAll('input').forEach(function(inp) {
                            if (inp.type === 'password' && inp.value) pass = inp.value;
                            if ((inp.type === 'text' || inp.type === 'email') && inp.value) user = inp.value;
                        });
                        if (user && pass && window.PRSPasswordBridge) {
                            window.PRSPasswordBridge.onPasswordDetected(user, pass);
                        }
                    });
                });
            })();
        """.trimIndent()
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
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

        view.evaluateJavascript(WEBRTC_BLOCK_SCRIPT, null)

        val cosmeticScript = adBlocker.cosmeticScript()
        if (cosmeticScript.isNotBlank()) view.evaluateJavascript(cosmeticScript, null)

        val autofill = passwordManager.autofillScript(currentHost())
        if (autofill.isNotBlank()) view.evaluateJavascript(autofill, null)

        view.evaluateJavascript(PASSWORD_DETECT_SCRIPT, null)

        val userScript = customScript()
        if (userScript.isNotBlank()) view.evaluateJavascript(userScript, null)

        onPageFinished(url)
    }
}
