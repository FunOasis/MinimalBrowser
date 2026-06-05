package com.minimalbrowser

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.minimalbrowser.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adBlocker: AdBlocker
    private var blockedCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Fix toolbar merging with status bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { view, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(0, statusBar, 0, 0)
            insets
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        adBlocker = AdBlocker.get(this)

        setupWebView()
        setupAddressBar()
        setupSwipeRefresh()

        val url = intent?.data?.toString() ?: Prefs.get(this).homepage
        loadUrl(url)
    }

    private fun setupWebView() {
        val wv = binding.webView
        wv.settings.apply {
            javaScriptEnabled     = Prefs.get(this@MainActivity).jsEnabled
            domStorageEnabled     = true
            loadWithOverviewMode  = true
            useWideViewPort       = true
            builtInZoomControls   = true
            displayZoomControls   = false
            setSupportZoom(true)
            cacheMode             = WebSettings.LOAD_DEFAULT
            mixedContentMode      = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled   = true
            // Fix some pages struggling to load
            userAgentString       = userAgentString.replace("wv", "")
            mediaPlaybackRequiresUserGesture = false
        }

        wv.webViewClient = BlockingWebViewClient(
            adBlocker         = adBlocker,
            onPageStarted     = { url -> onPageStarted(url) },
            onPageFinished    = { url -> onPageFinished(url) },
            onBlockedRequest  = {
                runOnUiThread {
                    blockedCount++
                    binding.blockedBadge.text = blockedCount.toString()
                    binding.blockedBadge.visibility = View.VISIBLE
                }
            },
            customScript      = { Prefs.get(this).customScript },
            onHomePageRequest = { showHomePage() }
        )

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                binding.progressBar.progress = newProgress
                binding.progressBar.visibility =
                    if (newProgress < 100) View.VISIBLE else View.GONE
            }
            override fun onReceivedTitle(view: WebView, title: String) {
                if (title != "P R S") binding.pageTitle.text = title
            }
        }
    }

    private fun showHomePage() {
        binding.webView.visibility  = View.GONE
        binding.homePage.visibility = View.VISIBLE
        binding.addressBar.setText("")
        binding.pageTitle.text = ""
        binding.blockedBadge.visibility = View.GONE
        blockedCount = 0
        // Show keyboard on home search bar
        binding.homeSearchBar.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        binding.homeSearchBar.postDelayed({
            imm.showSoftInput(binding.homeSearchBar, InputMethodManager.SHOW_IMPLICIT)
        }, 100)
    }

    private fun hideHomePage() {
        binding.homePage.visibility = View.GONE
        binding.webView.visibility  = View.VISIBLE
    }

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

        binding.homeSearchBar.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_GO ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                val query = binding.homeSearchBar.text.toString().trim()
                if (query.isNotBlank()) {
                    binding.homeSearchBar.setText("")
                    loadUrl(query)
                }
                true
            } else false
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            blockedCount = 0
            binding.blockedBadge.visibility = View.GONE
            binding.webView.reload()
        }
    }

    private fun loadUrl(input: String) {
        if (input.isBlank()) {
            showHomePage()
            return
        }

        hideHomePage()

        // Hide keyboard
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.addressBar.windowToken, 0)

        val url = when {
            input.startsWith("http://")  ||
            input.startsWith("https://") ||
            input.startsWith("file://")  -> input
            input.contains(".") &&
            !input.contains(" ")         -> "https://$input"
            else -> "https://duckduckgo.com/?q=${android.net.Uri.encode(input)}&kae=d&k1=-1"
        }

        blockedCount = 0
        binding.blockedBadge.visibility = View.GONE
        binding.webView.loadUrl(url)
        binding.addressBar.setText(url)
        binding.addressBar.clearFocus()
    }

    private fun onPageStarted(url: String) {
        runOnUiThread {
            binding.addressBar.setText(url)
            binding.btnBack.isEnabled    = binding.webView.canGoBack()
            binding.btnForward.isEnabled = binding.webView.canGoForward()
        }
    }

    private fun onPageFinished(url: String) {
        runOnUiThread {
            binding.swipeRefresh.isRefreshing = false
            binding.btnBack.isEnabled    = binding.webView.canGoBack()
            binding.btnForward.isEnabled = binding.webView.canGoForward()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.browser_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_home -> { showHomePage(); true }
            R.id.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.menu_stats -> {
                val s = adBlocker.stats()
                Toast.makeText(this,
                    "Blocked: $blockedCount requests this page\n" +
                    "Rules: ${s.hosts} hosts · ${s.patterns} patterns",
                    Toast.LENGTH_LONG).show()
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
            R.id.menu_about -> {
                AlertDialog.Builder(this)
                    .setTitle("P R S")
                    .setMessage("My first browser project with Claude.\n\nBuilt with WebView + uBlock-style ad blocking.\n\nVersion 1.0")
                    .setPositiveButton("OK", null)
                    .show()
                true
            }
            R.id.menu_exit -> {
                AlertDialog.Builder(this)
                    .setTitle("Exit")
                    .setMessage("Are you sure you want to exit?")
                    .setPositiveButton("Exit") { _, _ -> finishAffinity() }
                    .setNegativeButton("Cancel", null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.homePage.visibility == View.VISIBLE) {
            super.onBackPressed()
        } else if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            showHomePage()
        }
    }

    fun onNavBack(v: View)    { if (binding.webView.canGoBack())    binding.webView.goBack() }
    fun onNavForward(v: View) { if (binding.webView.canGoForward()) binding.webView.goForward() }
}
