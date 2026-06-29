package com.minimalbrowser

import android.graphics.Bitmap
import android.webkit.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
        private val ALLOWED_SCHEMES = setOf("http", "https")

        private val WEBRTC_BLOCK_SCRIPT = """
            (function() {
                try {
                    if (window.RTCPeerConnection) {
                        var Orig = window.RTCPeerConnection;
                        window.RTCPeerConnection = function(cfg) {
                            if (cfg && cfg.iceServers) cfg.iceServers = [];
                            return new Orig(cfg);
                        };
                        window.RTCPeerConnection.prototype = Orig.prototype;
                    }
                } catch(e) {}
            })();
        """.trimIndent()

        private val PASSWORD_DETECT_SCRIPT = """
            (function() {
                document.querySelectorAll('form').forEach(function(form) {
                    form.addEventListener('submit', function() {
                        var user = '', pass = '';
                        form.querySelectorAll('input').forEach(function(inp) {
                            if (inp.type === 'password' && inp.value) pass = inp.value;
                            if ((inp.type === 'text' || inp.type === 'email') && inp.value) user = inp.value;
                        });
                        if (user && pass && window.PRSPasswordBridge)
                            window.PRSPasswordBridge.onPasswordDetected(user, pass);
                    });
                });
            })();
        """.trimIndent()
    }

    override fun shouldInterceptRequest(
        view: WebView, request: WebResourceRequest
    ): WebResourceResponse? {
        val url = request.url.toString()
        val scheme = request.url.scheme?.lowercase() ?: ""
        
        if (scheme !in ALLOWED_SCHEMES) return EMPTY_RESPONSE

        if (adBlocker.isBlocked(url)) {
            onBlockedRequest()
            return EMPTY_RESPONSE
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun shouldOverrideUrlLoading(
        view: WebView, request: WebResourceRequest
    ): Boolean {
        val url = request.url.toString()
        val scheme = request.url.scheme?.lowercase() ?: ""

        if (url.startsWith("prs://")) {
            onHomePageRequest()
            return true
        }

        if (scheme !in ALLOWED_SCHEMES) return true
        return false
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onPageStarted(url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)

        // Ensure UI script evaluations run safely bound to main loop queue
        view.post {
            try {
                view.evaluateJavascript(WEBRTC_BLOCK_SCRIPT, null)

                val cosmetic = adBlocker.cosmeticScript()
                if (cosmetic.isNotBlank()) view.evaluateJavascript(cosmetic, null)

                view.evaluateJavascript(PASSWORD_DETECT_SCRIPT, null)

                val userScript = customScript()
                if (userScript.isNotBlank()) view.evaluateJavascript(userScript, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Handles the suspend function inside a safe Dispatchers.IO coroutine context
        val host = currentHost()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val autofill = passwordManager.autofillScript(host)
                if (autofill.isNotBlank()) {
                    view.post { view.evaluateJavascript(autofill, null) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        onPageFinished(url)
    }
}
