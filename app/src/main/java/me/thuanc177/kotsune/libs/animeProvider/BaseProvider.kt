package me.thuanc177.kotsune.libs.animeProvider

import android.util.Log
import androidx.compose.ui.unit.Constraints
import me.thuanc177.kotsune.libs.Constants
import me.thuanc177.kotsune.libs.Constants.APP_CACHE_DIR
import me.thuanc177.kotsune.libs.animeProvider.allanime.EpisodeInfo
import me.thuanc177.kotsune.libs.common.CachedRequestsSession
import me.thuanc177.kotsune.libs.common.Session
import me.thuanc177.kotsune.libs.common.StandardSession
import java.io.File

/**
 * Base class for anime providers
 */
abstract class AnimeProvider(
    cacheRequests: String,
    usePersistentProviderStore: String
) {
    companion object {
        private const val TAG = "AnimeProvider"

        // Generate a random user agent or use a predefined one
        val USER_AGENT: String = generateRandomUserAgent()

        /**
         * Generates a random user agent similar to yt_dlp's random_user_agent function
         */
        private fun generateRandomUserAgent(): String {
            // This is a simplified version - you may want to implement a more robust solution
            return "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
        }
    }

    /**
     * Get episode information for a specific anime
     * @param showId The ID of the anime
     * @param episodeNumStart The starting episode number
     * @param episodeNumEnd The ending episode number, defaults to a large number to get all episodes
     * @return List of episode information objects
     */
    open suspend fun getEpisodeInfos(
        showId: String,
        episodeNumStart: Float = 0f,
        episodeNumEnd: Float = 9999f
    ): Result<List<EpisodeInfo>> {
        // Default implementation returns an empty result
        // Subclasses should override this
        return Result.success(emptyList())
    }

    // Default headers that can be overridden by subclasses
    open val HEADERS: Map<String, String> = emptyMap()

    // HTTP session for making requests
    lateinit var session: Session

    // Store for provider-specific data
    lateinit var store: ProviderStore

    init {
        // Initialize the session based on cacheRequests parameter
        if (cacheRequests.lowercase() == "true") {
            val cacheDir = File(APP_CACHE_DIR, "cached_requests.db")
            val maxLifetime = System.getenv("FASTANIME_MAX_CACHE_LIFETIME")?.toIntOrNull() ?: 259200

            session = CachedRequestsSession(
                cacheDir.absolutePath,
                maxLifetime
            )
        } else {
            session = StandardSession()
        }

        // Update session headers with user agent and provider-specific headers
        session.updateHeaders(mapOf("User-Agent" to USER_AGENT) + HEADERS)

        // Initialize the store based on usePersistentProviderStore parameter
        if (usePersistentProviderStore.lowercase() == "true") {
            val storeFile = File(APP_CACHE_DIR, "anime_providers_store.db")
            store = ProviderStore(
                "persistent",
                this.javaClass.simpleName,
                storeFile.absolutePath
            )
        } else {
            store = ProviderStore("memory")
        }

        Log.d(TAG, "Initialized ${this.javaClass.simpleName} provider")
    }
}