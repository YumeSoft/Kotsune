package me.thuanc177.kotsune.config

import android.content.Context
import android.content.SharedPreferences

class AppConfig(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // Spicy Mode (NSFW content)
    var enableSpicyMode: Boolean
        get() = prefs.getBoolean(KEY_SPICY_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_SPICY_MODE, value).apply()

    // Chinese Origin content
    var enableChineseOrigin: Boolean
        get() = prefs.getBoolean(KEY_CHINESE_ORIGIN, false)
        set(value) = prefs.edit().putBoolean(KEY_CHINESE_ORIGIN, value).apply()

    // Default provider
    var defaultProvider: String
        get() = prefs.getString(KEY_DEFAULT_PROVIDER, "AllAnime") ?: "AllAnime"
        set(value) = prefs.edit().putString(KEY_DEFAULT_PROVIDER, value).apply()

    // Download path
    var downloadPath: String
        get() = prefs.getString(KEY_DOWNLOAD_PATH, DEFAULT_DOWNLOAD_PATH) ?: DEFAULT_DOWNLOAD_PATH
        set(value) = prefs.edit().putString(KEY_DOWNLOAD_PATH, value).apply()

    // Reset to defaults
    fun resetToDefaults() {
        prefs.edit().apply {
            putBoolean(KEY_SPICY_MODE, false)
            putBoolean(KEY_CHINESE_ORIGIN, false)
            putString(KEY_DEFAULT_PROVIDER, "AllAnime")
            putString(KEY_DOWNLOAD_PATH, DEFAULT_DOWNLOAD_PATH)
            apply()
        }
    }

    companion object {
        private const val PREF_NAME = "kotsune_config"
        private const val KEY_SPICY_MODE = "enable_spicy_mode"
        private const val KEY_CHINESE_ORIGIN = "enable_chinese_origin"
        private const val KEY_DEFAULT_PROVIDER = "default_provider"
        private const val KEY_DOWNLOAD_PATH = "download_path"
        private const val DEFAULT_DOWNLOAD_PATH = "/storage/emulated/0/Download/Kotsune"

        // Singleton instance
        @Volatile
        private var INSTANCE: AppConfig? = null

        fun getInstance(context: Context): AppConfig {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppConfig(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}