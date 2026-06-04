package com.minimalbrowser

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.minimalbrowser.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adBlocker: AdBlocker
    private var blockedCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        adBlocker = AdBlocker.get(this)

        setupWebView()
        setupAddressBar()
        setupSwipeRefresh()

        // Handle intent URLs (e.g. opened from another app)
        val url = intent?.data?.toString() ?: Prefs.get(this).homepage
        loadUrl(url)
    }

    // ── WebView setup ─────────────────────────────────────────────────────────

    private fun setupWebView() {
        val wv = binding.webView

        wv.settings.apply {
            javaScriptEnabled        = true
            domStorageEnabled        = true
            loadWithOverviewMode     = true
            useWideViewPort          = true
            builtInZoomControls      = true
            displayZoomControls      = false
            setSupportZoom(true)
            cacheMode                = WebSettings.LOAD_DEFAULT
            mixedContentMode         = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            // Privacy: disable telemetry leakage
            safeBrowsingEnabled      = true
        }

        // Respect user's JS toggle
        wv.settings.javaScriptEnabled = Prefs.get(this).jsEnabled

        wv.webViewClient = BlockingWebViewClient(
            adBlocker       = adBlocker,
            onPageStarted   = { url -> onPageStarted(url) },
            onPageFinished  = { url -> onPageFinished(url) },
            onBlockedRequest = {
                runOnUiThread {
                    blockedCount++
                    binding.blockedBadge.text = blockedCount.toString()
                    binding.blockedBadge.visibility = View.VISIBLE
                }
            }
        )

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                binding.progressBar.progress = newProgress
                binding.progressBar.visibility =
                    if (newProgress < 100) View.VISIBLE else View.GONE
            }

            override fun onReceivedTitle(view: WebView, title: String) {
                binding.pageTitle.text = title
            }
        }
    }

    // ── Address bar ───────────────────────────────────────────────────────────

    private fun setupAddressBar() {
        binding.addressBar.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                loadUrl(binding.addressBar.text.toString().trim())
                true
            } else false
        }

        binding.btnGo.setOnClickListener {
            loadUrl(binding.addressBar.text.toString().trim())
        }

        binding.addressBar.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.addressBar.selectAll()
        }
    }

    // ── SwipeRefresh ──────────────────────────────────────────────────────────

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            blockedCount = 0
            binding.blockedBadge.visibility = View.GONE
            binding.webView.reload()
        }
    }

    // ── Navigation helpers ────────────────────────────────────────────────────

    private fun loadUrl(input: String) {
        val url = when {
            input.isBlank()                          -> Prefs.get(this).homepage
            input.startsWith("http://")  ||
            input.startsWith("https://") ||
            input.startsWith("file://")              -> input
            input.contains(".")  &&
            !input.contains(" ")                     -> "https://$input"
            else -> "https://search.brave.com/search?q=${
                android.net.Uri.encode(input)
            }"
        }

        blockedCount = 0
        binding.blockedBadge.visibility = View.GONE
        binding.webView.loadUrl(url)
        binding.addressBar.clearFocus()
    }

    private fun onPageStarted(url: String) {
        runOnUiThread {
            binding.addressBar.setText(url)
            binding.btnBack.isEnabled = binding.webView.canGoBack()
            binding.btnForward.isEnabled = binding.webView.canGoForward()
        }
    }

    private fun onPageFinished(url: String) {
        runOnUiThread {
            binding.swipeRefresh.isRefreshing = false
            binding.btnBack.isEnabled = binding.webView.canGoBack()
            binding.btnForward.isEnabled = binding.webView.canGoForward()
        }
    }

    // ── Back / Forward buttons ────────────────────────────────────────────────

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.browser_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.menu_stats -> {
                val s = adBlocker.stats()
                Toast.makeText(
                    this,
                    "Blocked this page: $blockedCount requests\n" +
                    "Rules: ${s.hosts} hosts · ${s.patterns} patterns · ${s.cosmetic} cosmetic",
                    Toast.LENGTH_LONG
                ).show()
                true
            }
            R.id.menu_desktop -> {
                val wv = binding.webView
                val ua = wv.settings.userAgentString
                if (ua.contains("Mobile")) {
                    wv.settings.userAgentString = ua.replace("Mobile", "")
                    item.title = "Mobile Site"
                } else {
                    wv.settings.userAgentString = WebSettings.getDefaultUserAgent(this)
                    item.title = "Desktop Site"
                }
                wv.reload()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // System back → navigate WebView history
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    // Handle nav bar back/forward buttons in toolbar
    fun onNavBack(v: View)    { if (binding.webView.canGoBack())    binding.webView.goBack() }
    fun onNavForward(v: View) { if (binding.webView.canGoForward()) binding.webView.goForward() }
}
