package com.minimalbrowser

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.minimalbrowser.databinding.ActivityHistoryBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var historyManager: HistoryManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "History"

        historyManager = HistoryManager(this)

        binding.btnClearHistory.setOnClickListener {
            lifecycleScope.launch {
                historyManager.clear()
                loadHistory()
                Toast.makeText(this@HistoryActivity,
                    "History cleared", Toast.LENGTH_SHORT).show()
            }
        }

        loadHistory()
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            val entries = historyManager.getAll()
            binding.listHistory.removeAllViews()
            val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

            if (entries.isEmpty()) {
                val tv = TextView(this@HistoryActivity)
                tv.text = "No history"
                tv.setTextColor(0xFF888888.toInt())
                tv.setPadding(32, 32, 32, 32)
                binding.listHistory.addView(tv)
                return@launch
            }

            entries.forEach { entry ->
                val row = LayoutInflater.from(this@HistoryActivity)
                    .inflate(android.R.layout.simple_list_item_2,
                        binding.listHistory, false)
                row.findViewById<TextView>(android.R.id.text1).apply {
                    text = entry.title.ifBlank { entry.url }
                    setTextColor(0xFFFFFFFF.toInt())
                }
                row.findViewById<TextView>(android.R.id.text2).apply {
                    text = sdf.format(Date(entry.timestamp))
                    setTextColor(0xFF888888.toInt())
                }
                row.setBackgroundColor(0xFF111111.toInt())
                row.setOnClickListener {
                    startActivity(Intent(this@HistoryActivity,
                        MainActivity::class.java).apply {
                        data = Uri.parse(entry.url)
                    })
                    finish()
                }
                row.setOnLongClickListener {
                    lifecycleScope.launch {
                        historyManager.delete(entry.id)
                        loadHistory()
                    }
                    true
                }
                binding.listHistory.addView(row)

                val div = android.view.View(this@HistoryActivity)
                div.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1)
                div.setBackgroundColor(0xFF222222.toInt())
                binding.listHistory.addView(div)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed(); return true
    }
}
