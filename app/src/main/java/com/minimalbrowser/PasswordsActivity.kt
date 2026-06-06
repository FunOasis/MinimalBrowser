package com.minimalbrowser

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.minimalbrowser.databinding.ActivityPasswordsBinding
import kotlinx.coroutines.launch

class PasswordsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPasswordsBinding
    private lateinit var pm: PasswordManager
    private val EXPORT_REQUEST = 1001
    private val IMPORT_REQUEST = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPasswordsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Passwords"

        pm = PasswordManager(this)

        binding.btnExport.setOnClickListener { exportCsv() }
        binding.btnImport.setOnClickListener { importCsv() }
        binding.btnAdd.setOnClickListener    { showAddDialog() }

        loadPasswords()
    }

    private fun loadPasswords() {
        lifecycleScope.launch {
            val passwords = pm.getAll()
            binding.listPasswords.removeAllViews()

            if (passwords.isEmpty()) {
                val tv = TextView(this@PasswordsActivity)
                tv.text = "No saved passwords"
                tv.setTextColor(0xFF888888.toInt())
                tv.setPadding(32, 32, 32, 32)
                binding.listPasswords.addView(tv)
                return@launch
            }

            passwords.forEach { p ->
                val row = LayoutInflater.from(this@PasswordsActivity)
                    .inflate(android.R.layout.simple_list_item_2,
                        binding.listPasswords, false)
                row.findViewById<TextView>(android.R.id.text1).apply {
                    text = p.domain
                    setTextColor(0xFFFFFFFF.toInt())
                }
                row.findViewById<TextView>(android.R.id.text2).apply {
                    text = p.username
                    setTextColor(0xFF888888.toInt())
                }
                row.setOnLongClickListener {
                    AlertDialog.Builder(this@PasswordsActivity)
                        .setTitle("Delete password?")
                        .setMessage("${p.domain} — ${p.username}")
                        .setPositiveButton("Delete") { _, _ ->
                            lifecycleScope.launch {
                                pm.delete(p.id)
                                loadPasswords()
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                    true
                }
                row.setBackgroundColor(0xFF111111.toInt())
                binding.listPasswords.addView(row)

                val div = android.view.View(this@PasswordsActivity)
                div.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1)
                div.setBackgroundColor(0xFF222222.toInt())
                binding.listPasswords.addView(div)
            }
        }
    }

    private fun showAddDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        val etDomain   = EditText(this).apply {
            hint = "Domain (e.g. google.com)"
            setTextColor(0xFFFFFFFF.toInt()) }
        val etUsername = EditText(this).apply {
            hint = "Username / Email"
            setTextColor(0xFFFFFFFF.toInt()) }
        val etPassword = EditText(this).apply {
            hint = "Password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(0xFFFFFFFF.toInt()) }
        layout.addView(etDomain)
        layout.addView(etUsername)
        layout.addView(etPassword)

        AlertDialog.Builder(this)
            .setTitle("Add Password")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val d = etDomain.text.toString().trim()
                val u = etUsername.text.toString().trim()
                val p = etPassword.text.toString()
                if (d.isNotBlank() && u.isNotBlank() && p.isNotBlank()) {
                    lifecycleScope.launch {
                        pm.save(d, u, p)
                        loadPasswords()
                    }
                } else {
                    Toast.makeText(this, "All fields required",
                        Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportCsv() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, "prs_passwords.csv")
        }
        startActivityForResult(intent, EXPORT_REQUEST)
    }

    private fun importCsv() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, IMPORT_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            EXPORT_REQUEST -> {
                lifecycleScope.launch {
                    contentResolver.openOutputStream(uri)?.let { stream ->
                        pm.exportCsv(stream)
                        stream.close()
                        Toast.makeText(this@PasswordsActivity,
                            "Passwords exported", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            IMPORT_REQUEST -> {
                lifecycleScope.launch {
                    contentResolver.openInputStream(uri)?.let { stream ->
                        val count = pm.importCsv(stream)
                        stream.close()
                        Toast.makeText(this@PasswordsActivity,
                            "Imported $count passwords", Toast.LENGTH_SHORT).show()
                        loadPasswords()
                    }
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed(); return true
    }
}
