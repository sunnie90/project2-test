package com.gemquest.puzzle

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.gemquest.puzzle.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadSettings()

        binding.switchSound.setOnCheckedChangeListener { _, isChecked ->
            saveSettings("sound", isChecked)
        }

        binding.switchVibration.setOnCheckedChangeListener { _, isChecked ->
            saveSettings("vibration", isChecked)
        }

        binding.switchParticles.setOnCheckedChangeListener { _, isChecked ->
            saveSettings("particles", isChecked)
        }

        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("GemQuest", Context.MODE_PRIVATE)
        binding.switchSound.isChecked = prefs.getBoolean("sound", true)
        binding.switchVibration.isChecked = prefs.getBoolean("vibration", true)
        binding.switchParticles.isChecked = prefs.getBoolean("particles", true)
    }

    private fun saveSettings(key: String, value: Boolean) {
        val prefs = getSharedPreferences("GemQuest", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(key, value).apply()
    }

    companion object {
        fun getSetting(context: Context, key: String, defaultValue: Boolean = true): Boolean {
            val prefs = context.getSharedPreferences("GemQuest", Context.MODE_PRIVATE)
            return prefs.getBoolean(key, defaultValue)
        }
    }
}
