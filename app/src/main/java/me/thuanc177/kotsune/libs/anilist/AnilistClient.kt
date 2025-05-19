package me.thuanc177.kotsune.libs.anilist

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.thuanc177.kotsune.config.AppConfig
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import androidx.core.net.toUri

/**
 * Client for interacting with the AniList GraphQL API.
 * Uses the implicit OAuth grant flow for authentication.
 */
class AnilistClient(context: Context? = null) {
    private var appConfig: AppConfig? = if (context != null) AppConfig.getInstance(context) else null
    private val TAG = "AnilistClient"

    // Auth constants
    private val clientId = appConfig?.anilistClientId
    private val redirectUri = appConfig?.anilistRedirectUri
    private val tokenUrl = "https://anilist.co/api/v2/oauth/token"
    private val apiUrl = "https://graphql.anilist.co"

    /**
     * Check if user is authenticated with valid token
     */
    fun isUserAuthenticated(): Boolean {
        val token = appConfig?.anilistToken
        val expiration = appConfig?.anilistTokenExpiration

        Log.d(TAG, "Token: $token, Expiration: $expiration")
        val isValid =
            token?.isNotEmpty() == true && System.currentTimeMillis() < (expiration ?: 0L)

        Log.d(TAG, "User authentication status: $isValid")
        return isValid
    }

    /**
     * Open Anilist login page in browser
     */
    fun openAuthPage(context: Context) {
        val authUrl = "https://anilist.co/api/v2/oauth/authorize" +
                "?client_id=$clientId" +
                "&response_type=token"

        val intent = Intent(Intent.ACTION_VIEW, authUrl.toUri())
        context.startActivity(intent)
    }

    /**
     * Handle redirect from Anilist authorization
     */
    suspend fun handleAuthRedirect(uri: Uri?): Int = withContext(Dispatchers.IO) {
        if (uri == null) {
            Log.e(TAG, "Auth redirect URI is null")
            return@withContext AUTH_FAILURE
        }

        Log.d(TAG, "Processing OAuth redirect: $uri")

        // In implicit flow, access token comes directly in the fragment
        val fragment = uri.fragment
        if (fragment != null) {
            // Parse the fragment which has format "access_token=XXX&token_type=bearer&expires_in=YYY"
            val params = fragment.split("&").associate {
                val parts = it.split("=")
                if (parts.size == 2) parts[0] to parts[1] else "" to ""
            }

            val accessToken = params["access_token"]
            val expiresIn = params["expires_in"]?.toIntOrNull() ?: 0

            if (!accessToken.isNullOrEmpty()) {
                // Save the token and expiration
                val expirationTime = System.currentTimeMillis() + (expiresIn * 1000L)
                appConfig?.anilistToken = accessToken
                appConfig?.anilistTokenExpiration = expirationTime

                Log.d(TAG, "Successfully saved token, expires in ${expiresIn}s")
                return@withContext AUTH_SUCCESS
            }
        }

        return@withContext AUTH_FAILURE
    }
    /**
     * Execute a GraphQL query against the Anilist API
     */
    suspend fun executeQuery(query: String, variables: Map<String, Any>): Pair<Boolean, JSONObject?> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Executing GraphQL query with ${variables.size} variables")

            val url = URL(apiUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")

            // Add authorization header if we have a token
            appConfig?.anilistToken?.let {
                connection.setRequestProperty("Authorization", "Bearer $it")
                Log.d(TAG, "Added authorization header")
            }

            connection.doOutput = true

            // Prepare the request body
            val requestBody = JSONObject().apply {
                put("query", query)
                if (variables.isNotEmpty()) {
                    put("variables", JSONObject(variables))
                }
            }.toString()

            // Write the request body
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }

            // Get the response
            val responseCode = connection.responseCode
            Log.d(TAG, "GraphQL response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                Log.d(TAG, "Received GraphQL response with length: ${response.length}")

                // Parse the response
                val jsonResponse = JSONObject(response)

                // Check for errors
                if (jsonResponse.has("errors")) {
                    val errors = jsonResponse.getJSONArray("errors")
                    Log.e(TAG, "GraphQL errors: $errors")
                    return@withContext Pair(false, jsonResponse)
                }

                return@withContext Pair(true, jsonResponse)
            } else {
                val errorStream = connection.errorStream
                val errorResponse = errorStream?.let {
                    BufferedReader(InputStreamReader(it)).use { reader -> reader.readText() }
                }
                Log.e(TAG, "GraphQL query failed: $errorResponse")
                return@withContext Pair(false, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing GraphQL query", e)
            return@withContext Pair(false, null)
        }
    }

    /**
     * Logout user by clearing token
     */
    fun logout() {
        Log.d(TAG, "Logging out user, clearing token")
        appConfig?.anilistToken = ""
        appConfig?.anilistTokenExpiration = 0
    }

    /**
     * Get currently authenticated user
     */
    suspend fun getCurrentUser(): AnilistTypes.AnilistUser? {
        try {
            Log.d(TAG, "Fetching current user data")

            val query = """
                query {
                  Viewer {
                    id
                    name
                    avatar {
                      large
                      medium
                    }
                    bannerImage
                    options {
                      titleLanguage
                    }
                  }
                }
            """.trimIndent()

            val response = executeQuery(query, mapOf())

            if (response.first && response.second != null) {
                val data = response.second!!.getJSONObject("data")
                val viewer = data.getJSONObject("Viewer")

                val id = viewer.getInt("id")
                val name = viewer.getString("name")

                val avatarObj = viewer.optJSONObject("avatar")
                val avatar = if (avatarObj != null) {
                    AnilistTypes.AnilistImage(
                        large = avatarObj.optString("large"),
                        medium = avatarObj.optString("medium")
                    )
                } else null

                val bannerImage = viewer.optString("bannerImage", null)

                val user = AnilistTypes.AnilistUser(
                    id = id,
                    name = name,
                    avatar = avatar,
                    bannerImage = bannerImage
                )

                Log.d(TAG, "Successfully fetched user: $name (ID: $id)")
                return user
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching current user", e)
        }

        return null
    }

    companion object {
        const val AUTH_SUCCESS = 0
        const val AUTH_FAILURE = 1
    }

    init {
        // Load token from AppConfig if available
        appConfig?.let {
            val savedToken = it.anilistToken
            if (savedToken.isNotEmpty()) {
                Log.d(TAG, "Found saved access token with length: ${savedToken.length}")
            } else {
                Log.d(TAG, "No saved access token found")
            }
        }
    }

    /**
     * Get trending anime
     */
    suspend fun getTrendingAnime(
        type: String = "ANIME",
        page: Int = 1,
        perPage: Int = 15
    ): Pair<Boolean, JSONObject?> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching trending $type, page: $page, perPage: $perPage")

            val variables = mapOf(
                "type" to type,
                "page" to page,
                "perPage" to perPage,
                "isAdult" to false
            )

            return@withContext executeQuery(AniListQueries.TRENDING_QUERY, variables)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching trending anime", e)
            return@withContext Pair(false, null)
        }
    }

    /**
     * Get recently updated anime
     */
    suspend fun getRecentAnime(
        page: Int = 1,
        perPage: Int = 15
    ): Pair<Boolean, JSONObject?> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching recent anime, page: $page, perPage: $perPage")

            val variables = mapOf(
                "type" to "ANIME",
                "page" to page,
                "perPage" to perPage
            )

            return@withContext executeQuery(AniListQueries.MOST_RECENTLY_UPDATED_QUERY, variables)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching recent anime", e)
            return@withContext Pair(false, null)
        }
    }

    /**
     * Get highly rated anime
     */
    suspend fun getHighlyRatedAnime(
        page: Int = 1,
        perPage: Int = 15
    ): Pair<Boolean, JSONObject?> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching highly rated anime, page: $page, perPage: $perPage")

            val variables = mapOf(
                "type" to "ANIME",
                "page" to page,
                "perPage" to perPage
            )

            return@withContext executeQuery(AniListQueries.MOST_SCORED_QUERY, variables)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching highly rated anime", e)
            return@withContext Pair(false, null)
        }
    }

    /**
     * Search for anime based on various criteria
     */
    suspend fun searchAnime(
        perPage: Int = 50,
        page: Int = 1,
        query: String? = null,
        sort: List<String>? = null,
        genreIn: List<String>? = null,
        type: String = "ANIME",
        statusNot: String? = null
    ): Pair<Boolean, JSONObject?> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Searching anime: query='$query', page=$page, perPage=$perPage")

            val variables = mutableMapOf<String, Any>()
            query?.let { variables["query"] = it }
            sort?.let { variables["sort"] = it }
            genreIn?.let { variables["genre_in"] = it }
            variables["type"] = type

            // Only add status_not_in if statusNot is not null or empty
            if (!statusNot.isNullOrEmpty()) {
                variables["status_not_in"] = listOf(statusNot)
            }

            variables["perPage"] = perPage
            variables["page"] = page

            return@withContext executeQuery(AniListQueries.SEARCH_QUERY, variables)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching anime", e)
            return@withContext Pair(false, null)
        }
    }

    /**
     * Get detailed information about a specific anime
     */
    suspend fun getAnimeDetailed(id: Int): Pair<Boolean, JSONObject?> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching detailed info for anime ID: $id")

            val variables = mapOf("id" to id)
            return@withContext executeQuery(AniListQueries.ANIME_INFO_QUERY, variables)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching anime details for ID: $id", e)
            return@withContext Pair(false, null)
        }
    }

    /**
     * Toggle favorite status for an anime
     */
    suspend fun toggleFavorite(animeId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            if (appConfig?.anilistToken == null) {
                Log.w(TAG, "No access token available for toggleFavorite")
                return@withContext false
            }

            Log.d(TAG, "Toggling favorite for anime ID: $animeId")

            val variables = mapOf(
                "animeId" to animeId,
                "page" to 1
            )

            // This is a mutation query for toggling favorites
            val query = """
                mutation ToggleFavourite(${'$'}animeId: Int, ${'$'}page: Int) {
                  ToggleFavourite(animeId: ${'$'}animeId){
                    anime(page: ${'$'}page){
                      nodes {
                        id
                        isFavourite
                      }
                    }
                  }
                }
            """.trimIndent()

            // First toggle the favorite status
            val result = executeQuery(query, variables)
            if (!result.first) {
                Log.e(TAG, "Failed to toggle favorite for anime ID: $animeId")
                return@withContext false
            }

            // Then query the current favorite status
            val statusQuery = """
                query Query(${'$'}mediaId: Int) {
                  Media(id: ${'$'}mediaId) {
                    isFavourite
                    isFavouriteBlocked
                  }
                }
            """.trimIndent()

            val statusVariables = mapOf("mediaId" to animeId)
            val statusResult = executeQuery(statusQuery, statusVariables)

            if (statusResult.first && statusResult.second != null) {
                try {
                    val data = statusResult.second!!.getJSONObject("data")
                    val media = data.getJSONObject("Media")
                    return@withContext media.getBoolean("isFavourite")
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing favorite status", e)
                }
            }

            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling favorite", e)
            return@withContext false
        }
    }

    /**
     * Add anime to user's media list with specific status
     */
    suspend fun addToMediaList(animeId: Int, status: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (appConfig?.anilistToken == null) {
                Log.w(TAG, "No access token available for addToMediaList")
                return@withContext false
            }

            Log.d(TAG, "Adding anime ID: $animeId to media list with status: $status")

            val variables = mapOf(
                "mediaId" to animeId,
                "status" to status,
                "progress" to 0 // Default progress
            )

            val result = executeQuery(AniListQueries.MEDIA_LIST_MUTATION, variables)
            return@withContext result.first
        } catch (e: Exception) {
            Log.e(TAG, "Error adding to media list", e)
            return@withContext false
        }
    }

    /**
     * Mark an episode as watched for an anime by updating its progress
     */
    suspend fun markEpisodeAsWatched(animeId: Int, episodeNumber: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            if (appConfig?.anilistToken == null) {
                Log.w(TAG, "No access token available for markEpisodeAsWatched")
                return@withContext false
            }

            Log.d(TAG, "Marking episode $episodeNumber as watched for anime ID: $animeId")

            val variables = mapOf(
                "mediaId" to animeId,
                "progress" to episodeNumber
            )

            // Use the media list mutation with just the progress field
            return@withContext executeQuery(AniListQueries.MEDIA_LIST_MUTATION, variables).first
        } catch (e: Exception) {
            Log.e(TAG, "Error marking episode as watched", e)
            return@withContext false
        }
    }
}

data class AnilistTrackedMediaItem (
    val id: Int,
    val title: String,
    val imageUrl: String,
    val status: String,
    val progress: Int,
    val total: Int? = null,
    val score: Float? = null,
    val startDate: String? = null,
    val finishDate: String? = null,
    val rewatches: Int? = null,
    val notes: String? = null,
    val isPrivate: Boolean = false,
    val isFavorite: Boolean = false
)