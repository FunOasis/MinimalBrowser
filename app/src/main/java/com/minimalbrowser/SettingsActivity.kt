package com.minimalbrowser

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.minimalbrowser.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        prefs = Prefs.get(this)
        loadSettings()
        setupListeners()

        // Show adblocker rule stats
        val stats = AdBlocker.get(this).stats()
        binding.tvStats.text =
            "Active rules: ${stats.hosts} hosts · ${stats.patterns} filters · ${stats.cosmetic} cosmetic"
    }

    private fun loadSettings() {
        binding.switchAdblock.isChecked = prefs.adblockEnabled
        binding.switchJs.isChecked      = prefs.jsEnabled
        binding.etHomepage.setText(prefs.homepage)
    }

    private fun setupListeners() {
        binding.switchAdblock.setOnCheckedChangeListener { _, checked ->
            prefs.adblockEnabled = checked
        }

        binding.switchJs.setOnCheckedChangeListener { _, checked ->
            prefs.jsEnabled = checked
        }

        binding.btnSave.setOnClickListener {
            val hp = binding.etHomepage.text.toString().trim()
            prefs.homepage = if (hp.startsWith("http")) hp else "https://$hp"
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
