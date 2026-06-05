package com.minimalbrowser

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.minimalbrowser.databinding.ActivityTabsBinding

class TabsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTabsBinding
    private lateinit var tabManager: TabManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTabsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Tabs"

        tabManager = TabManager(this)

        binding.btnNewTab.setOnClickListener {
            // Open new browser instance (new task)
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
            }
            startActivity(intent)
            finish()
        }

        loadTabs()
    }

    private fun loadTabs() {
        binding.listTabs.removeAllViews()
        val tabs = tabManager.loadTabs()

        if (tabs.isEmpty()) {
            val tv = TextView(this)
            tv.text = "No saved tabs\n\nTap 'New Tab' to open a new window"
            tv.setTextColor(0xFF888888.toInt())
            tv.setPadding(32, 32, 32, 32)
            binding.listTabs.addView(tv)
            return
        }

        tabs.forEach { tab ->
            val row = LayoutInflater.from(this)
                .inflate(android.R.layout.simple_list_item_2, binding.listTabs, false)
            row.findViewById<TextView>(android.R.id.text1).apply {
                text = tab.title.ifBlank { tab.url }
                setTextColor(0xFFFFFFFF.toInt())
            }
            row.findViewById<TextView>(android.R.id.text2).apply {
                text = tab.url
                setTextColor(0xFF888888.toInt())
            }
            row.setBackgroundColor(0xFF111111.toInt())
            row.setOnClickListener {
                val intent = Intent(this, MainActivity::class.java).apply {
                    data  = Uri.parse(tab.url)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                }
                startActivity(intent)
                finish()
            }
            binding.listTabs.addView(row)

            val div = android.view.View(this)
            div.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            div.setBackgroundColor(0xFF222222.toInt())
            binding.listTabs.addView(div)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed(); return true
    }
}
