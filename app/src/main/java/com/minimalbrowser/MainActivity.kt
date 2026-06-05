package com.minimalbrowser

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.minimalbrowser.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adBlocker: AdBlocker
    private lateinit var passwordManager: PasswordManager
    private lateinit var historyManager: HistoryManager
    private lateinit var tabManager: TabManager
    private var blockedCount = 0
    private var currentHost = ""

    // Version tracking
    companion object {
        const val VERSION = "1.10"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { view, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updatePadding(top = top)
            insets
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        adBlocker       = AdBlocker.get(this)
        passwordManager = PasswordManager(this)
        historyManager  = HistoryManager(this)
        tabManager      = TabManager(this)

        FilterUpdateWorker.schedule(this)
        setupWebView()
        setupAddressBar()
        setupSwipeRefresh()
        setupBackHandler()

        if (savedInstanceState != null) {
            val url = savedInstanceState.getString("current_url")
            if (!url.isNullOrBlank() && !url.startsWith("prs://")) loadUrl(url)
            else showHomePage()
        } else {
            val intentUrl = intent?.data?.toString()
            if (!intentUrl.isNullOrBlank() && !intentUrl.startsWith("prs://")) loadUrl(intentUrl)
            else showHomePage()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val url = binding.webView.url
        if (!url.isNullOrBlank() && !url.startsWith("prs://")) {
            outState.putString("current_url", url)
        }
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this) {
            when {
                binding.homePage.visibility == View.VISIBLE -> { /* stay on home */ }
                binding.webView.canGoBack() -> binding.webView.goBack()
                else -> showHomePage()
            }
        }
    }

    private fun setupWebView() {
        val wv = binding.webView
        wv.settings.apply {
            javaScriptEnabled                = Prefs.get(this@MainActivity).jsEnabled
            domStorageEnabled                = true
            loadWithOverviewMode             = true
            useWideViewPort                  = true
            builtInZoomControls              = true
            displayZoomControls              = false
            setSupportZoom(true)
            cacheMode                        = WebSettings.LOAD_DEFAULT
            mixedContentMode                 = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled              = true
            userAgentString                  = userAgentString.replace("wv", "")
            mediaPlaybackRequiresUserGesture = false
            databaseEnabled                  = true
        }

        wv.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            try {
                val request = android.app.DownloadManager.Request(android.net.Uri.parse(url))
                request.setMimeType(mimeType)
                request.addRequestHeader("User-Agent", userAgent)
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                request.setTitle(fileName)
                request.setDescription("Downloading...")
                request.setNotificationVisibility(
                    android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                request.setDestinationInExternalPublicDir(
                    android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
                val dm = getSystemService(DOWNLOAD_SERVICE) as android.app.DownloadManager
                dm.enqueue(request)
                Toast.makeText(this, "Downloading $fileName", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
            }
        }

        wv.addJavascriptInterface(object : Any() {
            @JavascriptInterface
            fun onPasswordDetected(username: String, password: String) {
                runOnUiThread {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Save Password?")
                        .setMessage("Save password for $currentHost?")
                        .setPositiveButton("Save") { _, _ ->
                            passwordManager.save(currentHost, username, password)
                            Toast.makeText(this@MainActivity, "Password saved", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("Never", null).show()
                }
            }
        }, "PRSPasswordBridge")

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
            onHomePageRequest = { runOnUiThread { showHomePage() } },
            passwordManager   = passwordManager,
            currentHost       = { currentHost }
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

    fun showHomePage() {
        binding.webView.visibility  = View.GONE
        binding.homePage.visibility = View.VISIBLE
        binding.addressBar.setText("")
        binding.pageTitle.text      = ""
        binding.blockedBadge.visibility = View.GONE
        blockedCount = 0
        // Focus search bar and show keyboard
        binding.homeSearchBar.post {
            binding.homeSearchBar.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.homeSearchBar, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideHomePage() {
        binding.homePage.visibility = View.GONE
        binding.webView.visibility  = View.VISIBLE
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.homeSearchBar.windowToken, 0)
    }

    private fun setupAddressBar() {
        binding.addressBar.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                loadUrl(binding.addressBar.text.toString().trim()); true
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
                val q = binding.homeSearchBar.text.toString().trim()
                if (q.isNotBlank()) {
                    binding.homeSearchBar.setText("")
                    loadUrl(q)
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

    fun loadUrl(input: String) {
        if (input.isBlank() || input.startsWith("prs://")) {
            showHomePage(); return
        }
        hideHomePage()
        val url = when {
            input.startsWith("http://")  ||
            input.startsWith("https://") ||
            input.startsWith("file://")  -> input
            input.contains(".") && !input.contains(" ") -> "https://$input"
            else -> "https://duckduckgo.com/?q=${android.net.Uri.encode(input)}&kae=d&k1=-1"
        }
        blockedCount = 0
        binding.blockedBadge.visibility = View.GONE
        binding.webView.loadUrl(url)
        binding.addressBar.setText(url)
        binding.addressBar.clearFocus()
    }

    private fun onPageStarted(url: String) {
        if (url.isBlank() || url.startsWith("prs://")) return
        currentHost = try { java.net.URI(url).host ?: "" } catch (e: Exception) { "" }
        runOnUiThread {
            binding.addressBar.setText(url)
            binding.btnBack.isEnabled    = binding.webView.canGoBack()
            binding.btnForward.isEnabled = binding.webView.canGoForward()
        }
    }

    private fun onPageFinished(url: String) {
        if (url.isBlank() || url.startsWith("prs://")) return
        val title = binding.webView.title ?: url
        historyManager.save(url, title)
        runOnUiThread {
            binding.swipeRefresh.isRefreshing = false
            binding.btnBack.isEnabled    = binding.webView.canGoBack()
            binding.btnForward.isEnabled = binding.webView.canGoForward()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.browser_menu, menu); return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_home      -> { showHomePage(); true }
            R.id.menu_tabs      -> { startActivity(Intent(this, TabsActivity::class.java)); true }
            R.id.menu_history   -> { startActivity(Intent(this, HistoryActivity::class.java)); true }
            R.id.menu_passwords -> { startActivity(Intent(this, PasswordsActivity::class.java)); true }
            R.id.menu_settings  -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
            R.id.menu_stats     -> {
                val s = adBlocker.stats()
                Toast.makeText(this,
                    "Blocked: $blockedCount requests\nRules: ${s.hosts} hosts · ${s.patterns} patterns",
                    Toast.LENGTH_LONG).show(); true
            }
            R.id.menu_desktop   -> {
                val wv = binding.webView
                val ua = wv.settings.userAgentString
                if (ua.contains("Mobile")) {
                    wv.settings.userAgentString = ua.replace("Mobile", "")
                    item.title = "Mobile Site"
                } else {
                    wv.settings.userAgentString = WebSettings.getDefaultUserAgent(this)
                    item.title = "Desktop Site"
                }
                wv.reload(); true
            }
            R.id.menu_about -> {
                AlertDialog.Builder(this)
                    .setTitle("P R S")
                    .setMessage("My first browser project with Claude.\n\nBuilt with WebView + uBlock-style ad blocking.\n\nVersion $VERSION")
                    .setPositiveButton("OK", null).show(); true
            }
            R.id.menu_exit -> {
                AlertDialog.Builder(this)
                    .setTitle("Exit")
                    .setMessage("Are you sure you want to exit?")
                    .setPositiveButton("Exit") { _, _ -> finishAffinity() }
                    .setNegativeButton("Cancel", null).show(); true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun onNavBack(v: View)    { if (binding.webView.canGoBack())    binding.webView.goBack() }
    fun onNavForward(v: View) { if (binding.webView.canGoForward()) binding.webView.goForward() }

    override fun onPause()  { super.onPause();  binding.webView.onPause() }
    override fun onResume() { super.onResume(); binding.webView.onResume() }
    override fun onDestroy() {
        binding.webView.stopLoading()
        binding.webView.destroy()
        super.onDestroy()
    }
}
