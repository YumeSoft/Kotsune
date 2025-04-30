package me.thuanc177.kotsune.libs.animeProvider.nguonc

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.thuanc177.kotsune.libs.StreamLink
import me.thuanc177.kotsune.libs.StreamServer
import me.thuanc177.kotsune.libs.animeProvider.nguonc.NguonCConstants.FILM_API
import me.thuanc177.kotsune.libs.animeProvider.nguonc.NguonCConstants.HEADERS
import me.thuanc177.kotsune.libs.animeProvider.nguonc.NguonCConstants.SEARCH_API
import me.thuanc177.kotsune.libs.animeProvider.nguonc.NguonCConstants.SERVER_NAME
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Handles API requests to NguonC and extracts streams
 */
class NguonCExtractor(private val httpClient: NguonCHttpClient) {

    private val TAG = "NguonCExtractor"
    private val gson = Gson()

    /**
     * Search for anime by name
     */
    suspend fun searchAnime(query: String): List<NguonCTypes.NguonCSearchResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Searching for anime: $query")

            // Encode query
            val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
            val url = "$SEARCH_API?keyword=$encodedQuery"

            // Make the API request
            val response = httpClient.get(url, HEADERS)
            val searchResponse = gson.fromJson(response, NguonCTypes.NguonCSearchResponse::class.java)

            if (searchResponse.status == "success" && !searchResponse.data.isNullOrEmpty()) {
                Log.d(TAG, "Found ${searchResponse.data.size} results for query: $query")
                return@withContext searchResponse.data
            } else {
                Log.d(TAG, "No search results found for: $query")
                return@withContext emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching anime: ${e.message}", e)
            return@withContext emptyList()
        }
    }

    /**
     * Get detailed film information including episodes
     */
    suspend fun getFilmDetails(slug: String): NguonCTypes.NguonCFilmDetail? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting film details for: $slug")

            val url = "$FILM_API/$slug"
            val response = httpClient.get(url, HEADERS)

            val filmResponse = gson.fromJson(response, NguonCTypes.NguonCFilmResponse::class.java)

            if (filmResponse.status == "success") {
                Log.d(TAG, "Successfully fetched film details for: $slug")
                return@withContext filmResponse.movie
            } else {
                Log.d(TAG, "Failed to get film details for: $slug")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting film details: ${e.message}", e)
            return@withContext null
        }
    }

    /**
     * Extract streams from film details for a specific episode
     */
    fun extractStreams(filmDetails: NguonCTypes.NguonCFilmDetail, episodeNumber: String): List<StreamServer> {
        try {
            Log.d(TAG, "Extracting streams for episode: $episodeNumber")

            // Find the Vietsub server
            val vietsubServer = filmDetails.episodes.find {
                it.server_name.contains("Vietsub", ignoreCase = true)
            }

            if (vietsubServer == null) {
                Log.d(TAG, "No Vietsub server found")
                return emptyList()
            }

            // Find the specific episode
            val episode = vietsubServer.items.find {
                it.name == episodeNumber
            }

            if (episode == null) {
                Log.d(TAG, "Episode $episodeNumber not found")
                return emptyList()
            }

            // Create stream links
            val links = listOf(
                StreamLink(
                    link = episode.m3u8,
                    quality = "auto",
                    translationType = "sub"
                )
            )

            // Create a stream server
            val server = StreamServer(
                server = SERVER_NAME,
                links = links,
                episodeTitle = "${filmDetails.name} - Episode $episodeNumber",
                headers = mapOf("Referer" to "https://phim.nguonc.com/")
            )

            return listOf(server)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting streams: ${e.message}", e)
            return emptyList()
        }
    }
}

/**
 * HTTP client interface for NguonC
 */
interface NguonCHttpClient {
    suspend fun get(url: String, headers: Map<String, String> = mapOf()): String
    suspend fun post(url: String, body: String, headers: Map<String, String> = mapOf()): String
}

/**
 * Default implementation of NguonCHttpClient
 */
class DefaultNguonCHttpClient : NguonCHttpClient {
    private val client = okhttp3.OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    override suspend fun get(url: String, headers: Map<String, String>): String = withContext(Dispatchers.IO) {
        val request = okhttp3.Request.Builder()
            .url(url)
            .apply {
                headers.forEach { (name, value) ->
                    addHeader(name, value)
                }
            }
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Request failed with code: ${response.code}")
            response.body?.string() ?: throw Exception("Empty response body")
        }
    }

    override suspend fun post(url: String, body: String, headers: Map<String, String>): String = withContext(Dispatchers.IO) {
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val requestBody = okhttp3.RequestBody.create(mediaType, body)

        val request = okhttp3.Request.Builder()
            .url(url)
            .post(requestBody)
            .apply {
                headers.forEach { (name, value) ->
                    addHeader(name, value)
                }
            }
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Request failed with code: ${response.code}")
            response.body?.string() ?: throw Exception("Empty response body")
        }
    }

    private fun String.toMediaTypeOrNull(): okhttp3.MediaType? =
        this.toMediaTypeOrNull()
}