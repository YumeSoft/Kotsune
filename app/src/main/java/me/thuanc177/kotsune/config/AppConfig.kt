package me.thuanc177.kotsune.config

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.apply

class AppConfig(
    context: Context,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val securePrefs: SharedPreferences = context.getSharedPreferences(SECURE_PREF_NAME, Context.MODE_PRIVATE)
    private val encryptionHelper = EncryptionHelper()

    // Existing configuration
    var enableSpicyMode: Boolean
        get() = prefs.getBoolean(KEY_SPICY_MODE, false)
        set(value) = prefs.edit { putBoolean(KEY_SPICY_MODE, value) }

    var enableChineseOrigin: Boolean
        get() = prefs.getBoolean(KEY_CHINESE_ORIGIN, false)
        set(value) = prefs.edit { putBoolean(KEY_CHINESE_ORIGIN, value) }

    var defaultProvider: String
        get() = prefs.getString(KEY_DEFAULT_PROVIDER, "AllAnime") ?: "AllAnime"
        set(value) = prefs.edit { putString(KEY_DEFAULT_PROVIDER, value) }

    var downloadPath: String
        get() = prefs.getString(KEY_DOWNLOAD_PATH, DEFAULT_DOWNLOAD_PATH) ?: DEFAULT_DOWNLOAD_PATH
        set(value) = prefs.edit { putString(KEY_DOWNLOAD_PATH, value) }

    // ANIME
    // **********************************************************
    // MANGA

    // MangaDex API credentials
    var mangadexClientId: String
        get() = getSecureValue(KEY_MANGADEX_CLIENT_ID, "")
        set(value) = setSecureValue(KEY_MANGADEX_CLIENT_ID, value)

    var mangadexClientSecret: String
        get() = getSecureValue(KEY_MANGADEX_CLIENT_SECRET, "")
        set(value) = setSecureValue(KEY_MANGADEX_CLIENT_SECRET, value)

    var mangadexUsername: String
        get() = getSecureValue(KEY_MANGADEX_USERNAME, "")
        set(value) = setSecureValue(KEY_MANGADEX_USERNAME, value)

    var mangadexPassword: String
        get() = getSecureValue(KEY_MANGADEX_PASSWORD, "")
        set(value) = setSecureValue(KEY_MANGADEX_PASSWORD, value)

    // Cache for tokens
    var mangadexAccessToken: String
        get() = getSecureValue(KEY_MANGADEX_ACCESS_TOKEN, "")
        set(value) = setSecureValue(KEY_MANGADEX_ACCESS_TOKEN, value)

    var mangadexRefreshToken: String
        get() = getSecureValue(KEY_MANGADEX_REFRESH_TOKEN, "")
        set(value) = setSecureValue(KEY_MANGADEX_REFRESH_TOKEN, value)

    var mangadexTokenExpiry: Long
        get() = securePrefs.getLong(KEY_MANGADEX_TOKEN_EXPIRY, 0)
        set(value) = securePrefs.edit().putLong(KEY_MANGADEX_TOKEN_EXPIRY, value).apply()

    var contentFilters: Set<String>
        get() {
            val defaultFilters = setOf(CONTENT_FILTER_SAFE)
            val filtersString = prefs.getString(KEY_CONTENT_FILTERS, null) ?: return defaultFilters
            return filtersString.split(",").toSet()
        }
        set(value) {
            if (value.isEmpty()) {
                prefs.edit { putString(KEY_CONTENT_FILTERS, CONTENT_FILTER_SAFE) }
            } else {
                prefs.edit { putString(KEY_CONTENT_FILTERS, value.joinToString(",")) }
            }
        }

    // Helper methods for content filters
    fun isContentTypeEnabled(contentType: String): Boolean {
        return contentFilters.contains(contentType)
    }

    fun enableContentType(contentType: String) {
        val updatedFilters = contentFilters.toMutableSet()
        updatedFilters.add(contentType)
        contentFilters = updatedFilters
    }

    fun disableContentType(contentType: String) {
        val updatedFilters = contentFilters.toMutableSet()
        updatedFilters.remove(contentType)
        if (updatedFilters.isEmpty()) {
            updatedFilters.add(CONTENT_FILTER_SAFE)
        }
        contentFilters = updatedFilters
    }

    fun toggleContentType(contentType: String) {
        if (isContentTypeEnabled(contentType)) {
            disableContentType(contentType)
        } else {
            enableContentType(contentType)
        }
    }

    // Reset methods
    fun resetToDefaults() {
        prefs.edit().apply {
            putBoolean(KEY_SPICY_MODE, false)
            putBoolean(KEY_CHINESE_ORIGIN, false)
            putString(KEY_DEFAULT_PROVIDER, "AllAnime")
            putString(KEY_DOWNLOAD_PATH, DEFAULT_DOWNLOAD_PATH)
            putString(KEY_CONTENT_FILTERS, CONTENT_FILTER_SAFE)
            apply()
        }
    }

    // Check if MangaDex credentials are set
    fun hasMangadexCredentials(): Boolean {
        return mangadexClientId.isNotEmpty() &&
                mangadexClientSecret.isNotEmpty() &&
                mangadexUsername.isNotEmpty() &&
                mangadexPassword.isNotEmpty()
    }

    // Check if tokens are valid
    fun hasValidMangadexToken(): Boolean {
        return mangadexAccessToken.isNotEmpty() &&
                System.currentTimeMillis() < mangadexTokenExpiry
    }

    // Clear MangaDex credentials
    fun clearMangadexCredentials() {
        securePrefs.edit().apply {
            remove(KEY_MANGADEX_CLIENT_ID)
            remove(KEY_MANGADEX_CLIENT_SECRET)
            remove(KEY_MANGADEX_USERNAME)
            remove(KEY_MANGADEX_PASSWORD)
            remove(KEY_MANGADEX_ACCESS_TOKEN)
            remove(KEY_MANGADEX_REFRESH_TOKEN)
            remove(KEY_MANGADEX_TOKEN_EXPIRY)
            apply()
        }
    }

    // Helper methods for secure storage
    private fun getSecureValue(key: String, defaultValue: String): String {
        val encryptedValue = securePrefs.getString(key, null) ?: return defaultValue
        return try {
            encryptionHelper.decrypt(encryptedValue)
        } catch (e: Exception) {
            defaultValue
        }
    }

    private fun setSecureValue(key: String, value: String) {
        if (value.isEmpty()) {
            securePrefs.edit().remove(key).apply()
            return
        }
        try {
            val encryptedValue = encryptionHelper.encrypt(value)
            securePrefs.edit().putString(key, encryptedValue).apply()
        } catch (e: Exception) {
            Log.e("AppConfig", "Failed to encrypt value for key: $key", e)
        }
    }

    // Inner class for encryption
    private class EncryptionHelper {
        private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        private val keyAlias = "KotsuneSecretKey"
        private val transformation = "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_GCM}/${KeyProperties.ENCRYPTION_PADDING_NONE}"

        init {
            if (!keyStore.containsAlias(keyAlias)) {
                generateKey()
            }
        }

        private fun generateKey() {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
            )
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            keyGenerator.generateKey()
        }

        fun encrypt(plaintext: String): String {
            val cipher = Cipher.getInstance(transformation)
            val key = keyStore.getKey(keyAlias, null) as SecretKey
            cipher.init(Cipher.ENCRYPT_MODE, key)

            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)

            return Base64.encodeToString(combined, Base64.DEFAULT)
        }

        fun decrypt(encryptedText: String): String {
            val combined = Base64.decode(encryptedText, Base64.DEFAULT)

            val iv = combined.copyOfRange(0, 12) // GCM IV size is 12 bytes
            val encrypted = combined.copyOfRange(12, combined.size)

            val cipher = Cipher.getInstance(transformation)
            val key = keyStore.getKey(keyAlias, null) as SecretKey
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))

            val decryptedBytes = cipher.doFinal(encrypted)
            return String(decryptedBytes, Charsets.UTF_8)
        }
    }

    companion object {
        private const val PREF_NAME = "kotsune_config"
        private const val SECURE_PREF_NAME = "kotsune_secure_config"

        // Existing keys
        private const val KEY_SPICY_MODE = "enable_spicy_mode"
        private const val KEY_CHINESE_ORIGIN = "enable_chinese_origin"
        private const val KEY_DEFAULT_PROVIDER = "default_provider"
        private const val KEY_DOWNLOAD_PATH = "download_path"
        private const val DEFAULT_DOWNLOAD_PATH = "/storage/emulated/0/Download/Kotsune"

        // MangaDex API keys
        private const val KEY_MANGADEX_CLIENT_ID = "mangadex_client_id"
        private const val KEY_MANGADEX_CLIENT_SECRET = "mangadex_client_secret"
        private const val KEY_MANGADEX_USERNAME = "mangadex_username"
        private const val KEY_MANGADEX_PASSWORD = "mangadex_password"
        private const val KEY_MANGADEX_ACCESS_TOKEN = "mangadex_access_token"
        private const val KEY_MANGADEX_REFRESH_TOKEN = "mangadex_refresh_token"
        private const val KEY_MANGADEX_TOKEN_EXPIRY = "mangadex_token_expiry"
        // Content filter constants
        private const val KEY_CONTENT_FILTERS = "content_filters"
        // Content filter types
        const val CONTENT_FILTER_SAFE = "safe"
        const val CONTENT_FILTER_SUGGESTIVE = "suggestive"
        const val CONTENT_FILTER_EROTICA = "erotica"
        const val CONTENT_FILTER_PORNOGRAPHIC = "pornographic"

        // Singleton instance
        @Volatile
        private var INSTANCE: AppConfig? = null

        fun getInstance(context: Context?): AppConfig =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: context?.let {
                    AppConfig(it).also { instance ->
                        INSTANCE = instance
                    }
                } ?: throw IllegalStateException("Context is null")
            }
    }
}