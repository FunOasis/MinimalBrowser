package com.minimalbrowser

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import com.minimalbrowser.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: Prefs
    private var isSaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        prefs = Prefs.get(this)
        loadSettings()

        val stats = AdBlocker.get(this).stats()
        binding.tvStats.text =
            "Active rules: ${stats.hosts} hosts · ${stats.patterns} filters · ${stats.cosmetic} cosmetic"

        // Autosave toggles immediately
        binding.switchAdblock.setOnCheckedChangeListener { _, checked ->
            prefs.adblockEnabled = checked
        }
        binding.switchJs.setOnCheckedChangeListener { _, checked ->
            prefs.jsEnabled = checked
        }

        // Autosave text fields with debounce
        binding.etCustomFilters.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                prefs.customFilters = s.toString()
                AdBlocker.get(this@SettingsActivity).reloadCustomFilters()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.etCustomScript.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                prefs.customScript = s.toString()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun loadSettings() {
        binding.switchAdblock.isChecked    = prefs.adblockEnabled
        binding.switchJs.isChecked         = prefs.jsEnabled
        binding.etCustomFilters.setText(prefs.customFilters)
        binding.etCustomScript.setText(prefs.customScript)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed(); return true
    }
}
