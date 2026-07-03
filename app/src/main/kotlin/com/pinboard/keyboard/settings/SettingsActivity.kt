package com.pinboard.keyboard.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.google.android.material.slider.Slider
import com.pinboard.keyboard.R
import com.pinboard.keyboard.data.AppDatabase
import com.pinboard.keyboard.data.PinnedMessage
import com.pinboard.keyboard.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class SettingsActivity : AppCompatActivity() {

    private lateinit var settings: SettingsRepository
    private lateinit var dao: com.pinboard.keyboard.data.PinnedMessageDao

    private val createDocLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result -> handleBackupResult(result) }

    private val openDocLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result -> handleRestoreResult(result) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsRepository(this)
        dao = AppDatabase.getInstance(this).pinnedMessageDao()
        setContentView(R.layout.activity_settings)

        val switchDark = findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switchDarkMode)
        switchDark.isChecked = settings.darkMode == "dark"
        switchDark.setOnCheckedChangeListener { _, checked ->
            settings.darkMode = if (checked) "dark" else "light"
            AppCompatDelegate.setDefaultNightMode(
                if (checked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        val slider = findViewById<Slider>(R.id.sliderLength)
        val lengthValue = findViewById<android.widget.TextView>(R.id.lengthValue)
        slider.value = settings.generatorLength.toFloat()
        lengthValue.text = settings.generatorLength.toString()
        slider.addOnChangeListener { _, value, _ ->
            settings.generatorLength = value.toInt()
            lengthValue.text = value.toInt().toString()
        }

        findViewById<android.view.View>(R.id.btnBackup).setOnClickListener { startBackup() }
        findViewById<android.view.View>(R.id.btnRestore).setOnClickListener { startRestore() }
        findViewById<android.view.View>(R.id.btnClear).setOnClickListener { confirmClear() }
    }

    private fun startBackup() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "pinboard_backup.json")
        }
        createDocLauncher.launch(intent)
    }

    private fun startRestore() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        openDocLauncher.launch(intent)
    }

    private fun handleBackupResult(result: androidx.activity.result.ActivityResult) {
        if (result.resultCode != Activity.RESULT_OK) return
        val uri: Uri = result.data?.data ?: return
        lifecycleScope.launch {
            try {
                val messages = withContext(Dispatchers.IO) { dao.getAllOnce() }
                val arr = JSONArray()
                messages.forEach { m ->
                    arr.put(JSONObject().apply {
                        put("text", m.text)
                        put("createdAt", m.createdAt)
                        put("position", m.position)
                    })
                }
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { it.write(arr.toString().toByteArray()) }
                }
                Toast.makeText(this@SettingsActivity, R.string.backup_success, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, R.string.backup_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleRestoreResult(result: androidx.activity.result.ActivityResult) {
        if (result.resultCode != Activity.RESULT_OK) return
        val uri: Uri = result.data?.data ?: return
        lifecycleScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                } ?: return@launch
                val arr = JSONArray(json)
                val messages = mutableListOf<PinnedMessage>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    messages.add(
                        PinnedMessage(
                            text = obj.getString("text"),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            position = obj.optInt("position", 0)
                        )
                    )
                }
                withContext(Dispatchers.IO) { dao.insertAll(messages) }
                Toast.makeText(this@SettingsActivity, R.string.restore_success, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, R.string.restore_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmClear() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.clear_confirm_title)
            .setMessage(R.string.clear_confirm_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { dao.clearAll() }
                }
            }
            .show()
    }
}
