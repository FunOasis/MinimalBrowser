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

        val stats = AdBlocker.get(this).stats()
        binding.tvStats.text =
            "Active rules: ${stats.hosts} hosts · ${stats.patterns} filters · ${stats.cosmetic} cosmetic"

        binding.btnSave.setOnClickListener {
            prefs.jsEnabled     = binding.switchJs.isChecked
            prefs.adblockEnabled = binding.switchAdblock.isChecked
            prefs.customScript  = binding.etCustomScript.text.toString().trim()
            prefs.customFilters = binding.etCustomFilters.text.toString().trim()
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadSettings() {
        binding.switchAdblock.isChecked = prefs.adblockEnabled
        binding.switchJs.isChecked      = prefs.jsEnabled
        binding.etCustomScript.setText(prefs.customScript)
        binding.etCustomFilters.setText(prefs.customFilters)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
