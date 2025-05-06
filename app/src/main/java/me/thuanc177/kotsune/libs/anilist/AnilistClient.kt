package me.thuanc177.kotsune.libs.anilist

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
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
class AnilistClient(private val appConfig: AppConfig? = null) {

    companion object {
        private const val TAG = "AnilistClient"
        private const val ANILIST_AUTH_URL = "https://anilist.co/api/v2/oauth/authorize"
        private const val GRAPHQL_URL = "https://graphql.anilist.co"

        // Authentication status constants
        const val AUTH_FAILURE = -1
        const val AUTH_SUCCESS = 0
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
     * Build and return the authorization URL for implicit grant flow
     */
    fun getAuthUrl(): String {
        val authUri = ANILIST_AUTH_URL.toUri().buildUpon()
            .appendQueryParameter("client_id", appConfig?.anilistClientId)
            .appendQueryParameter("response_type", "token")
            .build()
            .toString()

        Log.d(TAG, "Generated auth URL: $authUri")
        return authUri
    }

    /**
     * Open Anilist OAuth page for implicit grant flow
     */
    fun openAuthPage(context: Context) {
        try {
            val clientId = appConfig?.anilistClientId ?: ""

            // Build auth URL with response_type=token for implicit grant
            val authUrl = "https://anilist.co/api/v2/oauth/authorize" +
                    "?client_id=$clientId" +
                    "&response_type=token"

            Log.d(TAG, "Opening auth URL: $authUrl")

            // Open in CustomTabsIntent for a better user experience
            val customTabsIntent = CustomTabsIntent.Builder().build()
            customTabsIntent.launchUrl(context, authUrl.toUri())
        } catch (e: Exception) {
            Log.e(TAG, "Error opening auth page", e)
        }
    }

    /**
     * Handle redirect from OAuth implicit grant flow
     * This will extract the access token directly from the URI fragment
     */
    fun handleAuthRedirect(uri: Uri?): Int {
        Log.d(TAG, "Handling auth redirect: $uri")

        if (uri == null) {
            Log.e(TAG, "Null URI in handleAuthRedirect")
            return AUTH_FAILURE
        }

        try {
            // Check if this is our app's redirect URI
            if (uri.scheme == "kotsune" && uri.host == "auth-callback") {
                Log.d(TAG, "Found kotsune://auth-callback URI")

                // The token might be in the fragment or in the query parameters
                val fragment = uri.fragment
                val query = uri.query

                Log.d(TAG, "URI Fragment: $fragment")
                Log.d(TAG, "URI Query: $query")

                // Try to extract token from fragment
                if (fragment != null && fragment.contains("access_token")) {
                    val params = fragment.split("&")
                    for (param in params) {
                        val parts = param.split("=", limit = 2)
                        if (parts.size == 2 && parts[0] == "access_token") {
                            val token = parts[1]
                            Log.d(TAG, "Found access token (first few chars): ${token.take(5)}...")

                            // Store the token
                            appConfig?.anilistToken = token

                            // Get expiration if available
                            val expiresIn = params.find { it.startsWith("expires_in=") }
                                ?.split("=", limit = 2)?.getOrNull(1)?.toIntOrNull() ?: 0

                            if (expiresIn > 0) {
                                val expirationTime = System.currentTimeMillis() + (expiresIn * 1000L)
                                appConfig?.anilistTokenExpiration = expirationTime
                                Log.d(TAG, "Token will expire at: ${expirationTime}")
                            }

                            return AUTH_SUCCESS
                        }
                    }
                }

                // If we couldn't find the token in the fragment, try the query
                if (query != null && query.contains("access_token")) {
                    val params = query.split("&")
                    for (param in params) {
                        val parts = param.split("=", limit = 2)
                        if (parts.size == 2 && parts[0] == "access_token") {
                            val token = parts[1]
                            Log.d(TAG, "Found access token in query (first few chars): ${token.take(5)}...")

                            // Store the token
                            appConfig?.anilistToken = token
                            return AUTH_SUCCESS
                        }
                    }
                }

                Log.e(TAG, "No access token found in URI")
                return AUTH_FAILURE
            }

            return AUTH_FAILURE
        } catch (e: Exception) {
            Log.e(TAG, "Error handling auth redirect", e)
            return AUTH_FAILURE
        }
    }

    /**
     * Log out current user and clear token
     */
    fun logout() {
        Log.d(TAG, "Logging out user")
        appConfig?.anilistToken = ""
    }

    /**
     * Check if user is authenticated
     */
    fun isUserAuthenticated(): Boolean {
        val isAuthenticated = appConfig?.anilistToken?.isNotEmpty() == true
        Log.d(TAG, "User authentication status: $isAuthenticated")
        return isAuthenticated
    }

    /**
     * Authenticate user and retrieve user information
     */
    suspend fun getCurrentUser(): AnilistTypes.AnilistUser? = withContext(Dispatchers.IO) {
        if (appConfig?.anilistToken == null) {
            Log.w(TAG, "No access token available for getCurrentUser")
            return@withContext null
        }

        try {
            Log.d(TAG, "Fetching current user data")
            val response = executeQuery(
                AniListQueries.GET_LOGGED_IN_USER_QUERY,
                emptyMap()
            )

            if (!response.first || response.second == null) {
                Log.e(TAG, "Failed to fetch user data")
                return@withContext null
            }

            val jsonResponse = response.second!!

            if (!jsonResponse.has("data") || !jsonResponse.getJSONObject("data").has("Viewer")) {
                Log.e(TAG, "Invalid user response: $jsonResponse")
                return@withContext null
            }

            val viewerJson = jsonResponse.getJSONObject("data").getJSONObject("Viewer")

            // Parse user data
            val userData = AnilistTypes.AnilistUser(
                id = viewerJson.optInt("id"),
                name = viewerJson.optString("name"),
                bannerImage = viewerJson.optString("bannerImage"),
                avatar = if (viewerJson.has("avatar")) {
                    val avatarJson = viewerJson.getJSONObject("avatar")
                    AnilistTypes.AnilistImage(
                        medium = avatarJson.optString("medium"),
                        large = avatarJson.optString("large")
                    )
                } else null
            )

            Log.d(TAG, "Successfully fetched user: ${userData.name} (ID: ${userData.id})")
            return@withContext userData

        } catch (e: Exception) {
            Log.e(TAG, "Error getting current user", e)
            return@withContext null
        }
    }

    /**
     * Execute a GraphQL query with the authenticated token
     */
    suspend fun executeQuery(
        query: String,
        variables: Map<String, Any>
    ): Pair<Boolean, JSONObject?> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Executing GraphQL query with ${variables.size} variables")

            if (appConfig?.anilistToken == null) {
                Log.w(TAG, "No access token available for GraphQL query")
            }

            val url = URL(GRAPHQL_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")

            // Add authorization header if we have a token
            appConfig?.anilistToken?.let {
                connection.setRequestProperty("Authorization", "Bearer $it")
                Log.d(TAG, "Added authorization header")
            }

            connection.doInput = true
            connection.doOutput = true

            // Prepare the request body
            val requestBody = JSONObject().apply {
                put("query", query)
                put("variables", JSONObject(variables))
            }

            // Write the request body
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }

            // Check response code
            val responseCode = connection.responseCode
            Log.d(TAG, "GraphQL response code: $responseCode")

            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorStream = connection.errorStream
                val errorResponse = if (errorStream != null) {
                    BufferedReader(InputStreamReader(errorStream)).use { reader ->
                        reader.readText()
                    }
                } else {
                    "No error details available"
                }
                Log.e(TAG, "HTTP Error: $responseCode, $errorResponse")
                return@withContext Pair(false, null)
            }

            // Read the response
            val response = BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                reader.readText()
            }

            Log.d(TAG, "Received GraphQL response with length: ${response.length}")
            return@withContext Pair(true, JSONObject(response))

        } catch (e: Exception) {
            Log.e(TAG, "Error executing GraphQL query", e)
            return@withContext Pair(false, null)
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
    suspend fun toggleFavorite(animeId: Int, favorite: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            if (appConfig?.anilistToken == null) {
                Log.w(TAG, "No access token available for toggleFavorite")
                return@withContext false
            }

            Log.d(TAG, "Toggling favorite for anime ID: $animeId, favorite: $favorite")

            val variables = mapOf(
                "animeId" to animeId,
                "favorite" to favorite
            )

            // This is a mutation query for toggling favorites
            val query = """
                mutation (${'$'}animeId: Int, ${'$'}favorite: Boolean) {
                  ToggleFavourite(animeId: ${'$'}animeId, favourite: ${'$'}favorite) {
                    anime {
                      id
                      isFavourite
                    }
                  }
                }
            """.trimIndent()

            val result = executeQuery(query, variables)
            return@withContext result.first
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
