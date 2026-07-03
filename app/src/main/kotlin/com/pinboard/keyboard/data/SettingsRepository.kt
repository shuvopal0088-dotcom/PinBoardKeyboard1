package com.pinboard.keyboard.data

import android.content.Context
import android.content.SharedPreferences

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("pinboard_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DARK_MODE = "dark_mode" // "system" | "light" | "dark"
        private const val KEY_GENERATOR_LENGTH = "generator_length"
        const val DEFAULT_GENERATOR_LENGTH = 7
    }

    var darkMode: String
        get() = prefs.getString(KEY_DARK_MODE, "system") ?: "system"
        set(value) = prefs.edit().putString(KEY_DARK_MODE, value).apply()

    var generatorLength: Int
        get() = prefs.getInt(KEY_GENERATOR_LENGTH, DEFAULT_GENERATOR_LENGTH)
        set(value) = prefs.edit().putInt(KEY_GENERATOR_LENGTH, value.coerceIn(6, 20)).apply()
}
