package me.thuanc177.kotsune.libs

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.Anime
import me.thuanc177.kotsune.libs.animeProvider.allanime.EpisodeInfo
import org.json.JSONObject

interface AnimeProvider {
    suspend fun searchForAnime(anilistId: Int, query: String, translationType: String = "sub"): Result<Anime>
    suspend fun getAnimeAlternativeId(
        animeTitle: String,
        anilistId: Int
    ): Result<Anime>

    suspend fun getEpisodeList(showId: String, episodeNumStart: Float, episodeNumEnd: Float): Result<List<EpisodeInfo>>
    suspend fun getEpisodeStreams(animeId: String, episode: String, translationType: String = "sub"): Result<List<StreamServer>>
}

/**
 * Base implementation for anime streaming providers
 */
abstract class BaseAnimeProvider(val name: String) : AnimeProvider {
    protected open val TAG = "AnimeProvider_$name"

    protected suspend fun <T> apiRequest(block: suspend () -> T): Result<T> {
        return try {
            withContext(Dispatchers.IO) {
                Result.success(block())
            }
        } catch (e: Exception) {
            Log.e(TAG, "API request failed", e)
            Result.failure(e)
        }
    }

    protected fun logDebug(message: String) {
        Log.d(TAG, message)
    }

    protected fun logError(message: String, error: Throwable? = null) {
        if (error != null) {
            Log.e(TAG, message, error)
        } else {
            Log.e(TAG, message)
        }
    }
}

/**
 * Represents a streaming server with video links and metadata
 */
data class StreamServer(
    val server: String,
    val links: List<StreamLink>,
    val episodeTitle: String,
    val subtitles: List<Subtitle> = emptyList(),
    val headers: Map<String, String> = emptyMap()
)

/**
 * Represents a video stream link with quality information
 */
data class StreamLink(
    val link: String,
    val quality: String = "unknown",
    val translationType: String = "sub"
)

/**
 * Represents a subtitle track
 */
data class Subtitle(
    val url: String,
    val language: String
)

/**
 * Utility to handle AniSkip integration for opening/ending skip times
 */
class AniSkip {
    companion object {
        private const val ANISKIP_ENDPOINT = "https://api.aniskip.com/v1/skip-times"

        suspend fun getSkipTimes(malId: Int, episodeNumber: Float, types: List<String> = listOf("op", "ed")): Result<JSONObject> {
            return try {
                withContext(Dispatchers.IO) {
                    val typesParam = types.joinToString("&") { "types=$it" }
                    val url = "$ANISKIP_ENDPOINT/$malId/$episodeNumber?$typesParam"
                    val response = HttpClient.get(url)
                    Result.success(JSONObject(response))
                }
            } catch (e: Exception) {
                Log.e("AniSkip", "Failed to get skip times", e)
                Result.failure(e)
            }
        }
    }
}

/**
 * Simple HTTP client abstraction
 */
object HttpClient {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        // Implementation using OkHttp or another HTTP client
        // This is a placeholder - actual implementation needed
        return ""
    }
}