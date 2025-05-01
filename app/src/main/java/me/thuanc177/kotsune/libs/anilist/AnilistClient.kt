package me.thuanc177.kotsune.libs.anilist

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.thuanc177.kotsune.config.AppConfig
import org.json.JSONObject
import retrofit2.Response
import java.util.UUID

/**
 * Client for interacting with the AniList GraphQL API.
 * Uses Retrofit for network operations.
 */
class AnilistClient(private val appConfig: AppConfig? = null) {
    private var token: String? = null
    private var userId: Int? = null

    companion object {
        private const val TAG = "AnilistClient"
        private const val CLIENT_ID = "" // Your ClientID here
        private const val CLIENT_SECRET= "" // Your ClientSecret here
        private const val REDIRECT_URI = "kotsune://auth-callback"
        private const val OAUTH_URL = "https://anilist.co/api/v2/oauth/authorize"

        // Authentication status constants
        const val AUTH_SUCCESS = 0
        const val AUTH_ERROR = 1
        const val AUTH_CANCELLED = 2
    }

    init {
        // Load token from AppConfig if available
        appConfig?.let {
            val savedToken = it.anilistToken
            if (savedToken.isNotEmpty()) {
                token = savedToken
                // In a real app, we would validate the token here
            }
        }
    }

    /**
     * Set authentication token without logging in
     */
    fun setToken(token: String) {
        this.token = token
        // Save token if AppConfig is available
        appConfig?.anilistToken = token
    }

    /**
     * Get authentication URL for AniList OAuth
     */
    fun getAuthUrl(): String {
        val encodedRedirectUri = Uri.encode(REDIRECT_URI)
        val state = UUID.randomUUID().toString() // CSRF protection
        val url = "$OAUTH_URL?client_id=$CLIENT_ID&redirect_uri=$encodedRedirectUri&response_type=code"
        Log.d(TAG, "Generated auth URL: $url")
        return url
    }

    /**
     * Open authentication page in browser
     */
    fun openAuthPage(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getAuthUrl()))
        context.startActivity(intent)
    }

    /**
     * Handle OAuth redirect URI and extract authorization code
     * Returns AUTH_SUCCESS if successful, AUTH_ERROR or AUTH_CANCELLED otherwise
     */
    suspend fun handleAuthRedirect(uri: Uri?, expectedState: String? = null): Int {
        Log.d(TAG, "Handling auth redirect URI: $uri, Query: ${uri?.query}, Fragment: ${uri?.fragment}")

        if (uri == null) {
            Log.e(TAG, "Auth redirect failed: URI is null")
            return AUTH_CANCELLED
        }

        if (!uri.toString().startsWith(REDIRECT_URI)) {
            Log.e(TAG, "Auth redirect failed: URI doesn't match expected redirect URI. Got: ${uri.toString()}")
            return AUTH_CANCELLED
        }

        // Check for error in query parameters
        val error = uri.getQueryParameter("error")
        if (error != null) {
            Log.e(TAG, "Auth error from AniList: $error")
            return AUTH_ERROR
        }

        // Verify state parameter (optional, for CSRF protection)
        val state = uri.getQueryParameter("state")
        if (expectedState != null && state != expectedState) {
            Log.e(TAG, "Auth redirect failed: State mismatch. Expected: $expectedState, Got: $state")
            return AUTH_ERROR
        }

        // Extract authorization code from query parameters
        val code = uri.getQueryParameter("code")
        if (code != null) {
            Log.d(TAG, "Found authorization code, length: ${code.length}")
            // Exchange code for token
            val tokenResponse = exchangeCodeForToken(code)
            if (tokenResponse) {
                return AUTH_SUCCESS
            }
            return AUTH_ERROR
        }

        // Fallback to check if token is in fragment (Implicit flow)
        val fragment = uri.fragment
        if (fragment != null) {
            Log.d(TAG, "Auth URI fragment: $fragment")
            val params = fragment.split("&")
            for (param in params) {
                val parts = param.split("=")
                if (parts.size == 2 && parts[0] == "access_token") {
                    Log.d(TAG, "Found access token in fragment, length: ${parts[1].length}")
                    setToken(parts[1])
                    return AUTH_SUCCESS
                }
                if (parts.size == 2 && parts[0] == "error") {
                    Log.e(TAG, "Auth error from AniList: ${parts[1]}")
                    return AUTH_ERROR
                }
            }
        }

        Log.e(TAG, "Auth redirect failed: No access_token or authorization code found")
        return AUTH_ERROR
    }

    /**
     * Exchange authorization code for access token
     */
    private suspend fun exchangeCodeForToken(code: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Exchanging code for token: $code")

            // CLIENT SECRET SHOULD BE KEPT PRIVATE
            // Consider using BuildConfig or other secure storage methods
            val response = RetrofitClient.tokenService.exchangeToken(
                grantType = "authorization_code",
                clientId = CLIENT_ID,
                clientSecret = CLIENT_SECRET,
                redirectUri = REDIRECT_URI,
                code = code
            )

            val rawResponseBody = response.body()
            val rawErrorBody = response.errorBody()?.string()

            Log.d(TAG, "Token exchange response code: ${response.code()}")
            Log.d(TAG, "Raw response body: $rawResponseBody")

            if (!response.isSuccessful) {
                Log.e(TAG, "Error body: $rawErrorBody")
                return@withContext false
            }

            if (response.isSuccessful && rawResponseBody != null) {
                if (rawResponseBody is TokenResponse && rawResponseBody.accessToken != null) {
                    Log.d(TAG, "Successfully received access token")
                    setToken(rawResponseBody.accessToken)
                    return@withContext true
                } else {
                    Log.e(TAG, "Token response missing access_token: $rawResponseBody")
                }
            }

            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Error exchanging code for token", e)
            return@withContext false
        }
    }

    /**
     * Log out current user and clear token
     */
    fun logout() {
        token = null
        userId = null
        appConfig?.anilistToken = ""
    }

    /**
     * Authenticate user and retrieve user information
     */
    suspend fun getCurrentUser(): AnilistTypes.AnilistUser? = withContext(Dispatchers.IO) {
        if (token == null) {
            Log.w(TAG, "No token available for getCurrentUser")
            return@withContext null
        }

        try {
            val response = executeAuthenticatedQuery(
                AniListQueries.GET_LOGGED_IN_USER_QUERY,
                emptyMap()
            )

            if (!response.isSuccessful || response.body() == null) {
                Log.e(TAG, "Failed to fetch user: ${response.errorBody()?.string()}")
                return@withContext null
            }

            // Get the raw response as a Map and extract data
            val responseBody = RetrofitClient.gson.toJson(response.body())
            val jsonResponse = JSONObject(responseBody)

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
                avatar = (if (viewerJson.has("avatar")) {
                    val avatarJson = viewerJson.getJSONObject("avatar")
                    AnilistTypes.AnilistImage(
                        medium = avatarJson.optString("medium"),
                        large = avatarJson.optString("large")
                    )
                } else null).toString()
            )

            userId = userData.id
            Log.d(TAG, "Fetched user: ${userData.name} (ID: ${userData.id})")
            return@withContext userData

        } catch (e: Exception) {
            Log.e(TAG, "Error getting current user", e)
            return@withContext null
        }
    }

    /**
     * Execute a GraphQL query with authentication if token is available
     */
    private suspend fun executeAuthenticatedQuery(
        query: String,
        variables: Map<String, Any>
    ): Response<Map<String, Any>> = withContext(Dispatchers.IO) {
        val request = GraphqlRequest(query, variables)
        val authHeader = token?.let { "Bearer $it" }
        return@withContext RetrofitClient.service.executeQuery(request, authHeader)
    }

    /**
     * Execute a raw GraphQL query with authentication
     */
    suspend fun executeQuery(
        query: String,
        variables: Map<String, Any>
    ): Pair<Boolean, JSONObject?> = withContext(Dispatchers.IO) {
        try {
            val response = executeAuthenticatedQuery(query, variables)
            if (response.isSuccessful && response.body() != null) {
                val jsonString = RetrofitClient.gson.toJson(response.body())
                return@withContext Pair(true, JSONObject(jsonString))
            } else {
                Log.e(TAG, "Query failed: ${response.errorBody()?.string()}")
                return@withContext Pair(false, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing query", e)
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
            val variables = mapOf(
                "type" to type,
                "page" to page,
                "perPage" to perPage
            )

            val response = executeAuthenticatedQuery(AniListQueries.TRENDING_QUERY, variables)
            if (response.isSuccessful && response.body() != null) {
                val jsonString = RetrofitClient.gson.toJson(response.body())
                return@withContext Pair(true, JSONObject(jsonString))
            } else {
                Log.e(TAG, "Failed to fetch trending anime: ${response.errorBody()?.string()}")
                return@withContext Pair(false, null)
            }
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
            val variables = mapOf(
                "type" to "ANIME",
                "page" to page,
                "perPage" to perPage
            )

            val response = executeAuthenticatedQuery(AniListQueries.MOST_RECENTLY_UPDATED_QUERY, variables)
            if (response.isSuccessful && response.body() != null) {
                val jsonString = RetrofitClient.gson.toJson(response.body())
                return@withContext Pair(true, JSONObject(jsonString))
            } else {
                Log.e(TAG, "Failed to fetch recent anime: ${response.errorBody()?.string()}")
                return@withContext Pair(false, null)
            }
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
            val variables = mapOf(
                "type" to "ANIME",
                "page" to page,
                "perPage" to perPage
            )

            val response = executeAuthenticatedQuery(AniListQueries.MOST_SCORED_QUERY, variables)
            if (response.isSuccessful && response.body() != null) {
                val jsonString = RetrofitClient.gson.toJson(response.body())
                return@withContext Pair(true, JSONObject(jsonString))
            } else {
                Log.e(TAG, "Failed to fetch highly rated anime: ${response.errorBody()?.string()}")
                return@withContext Pair(false, null)
            }
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
        status_not: String? = null
    ): Pair<Boolean, JSONObject?> = withContext(Dispatchers.IO) {
        try {
            val variables = mutableMapOf<String, Any>()
            query?.let { variables["query"] = it }
            sort?.let { variables["sort"] = it }
            genreIn?.let { variables["genre_in"] = it }
            variables["type"] = type

            // Only add status_not_in if status_not is not null or empty
            if (!status_not.isNullOrEmpty()) {
                variables["status_not_in"] = listOf(status_not)
            }

            variables["perPage"] = perPage
            variables["page"] = page

            val response = executeAuthenticatedQuery(
                AniListQueries.SEARCH_QUERY,
                variables
            )

            if (response.isSuccessful && response.body() != null) {
                val jsonString = RetrofitClient.gson.toJson(response.body())
                return@withContext Pair(true, JSONObject(jsonString))
            } else {
                Log.e(TAG, "Failed to search anime: ${response.errorBody()?.string()}")
                return@withContext Pair(false, null)
            }
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
            val variables = mapOf(
                "id" to id
            )

            val response = executeAuthenticatedQuery(AniListQueries.ANIME_INFO_QUERY, variables)
            if (response.isSuccessful && response.body() != null) {
                val rawJson = JSONObject(RetrofitClient.gson.toJson(response.body()))
                Log.d(TAG, "Anime details response: $rawJson")
                return@withContext Pair(true, rawJson)
            } else {
                Log.e(TAG, "Failed to fetch anime details: ${response.errorBody()?.string()}")
                return@withContext Pair(false, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching anime details", e)
            return@withContext Pair(false, null)
        }
    }

    /**
     * Toggle favorite status for an anime
     */
    suspend fun toggleFavorite(animeId: Int, favorite: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            if (token == null) {
                Log.w(TAG, "No token available for toggleFavorite")
                return@withContext false
            }

            val variables = mapOf(
                "animeId" to animeId,
                "favorite" to favorite
            )

            // This is a mutation query for toggling favorites
            val query = """
                mutation (${'$'}animeId: Int, ${'$'}favorite: Boolean) {
                  ToggleFavourite(animeId: ${'$'}animeId, favourite: ${'$'}favorite) {
                    anime {
                      nodes {
                        id
                        isFavourite
                      }
                    }
                  }
                }
            """.trimIndent()

            val response = executeAuthenticatedQuery(query, variables)
            if (response.isSuccessful) {
                Log.d(TAG, "Toggled favorite for animeId: $animeId, favorite: $favorite")
                return@withContext true
            } else {
                Log.e(TAG, "Failed to toggle favorite: ${response.errorBody()?.string()}")
                return@withContext false
            }
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
            if (token == null) {
                Log.w(TAG, "No token available for addToMediaList")
                return@withContext false
            }

            val variables = mapOf(
                "mediaId" to animeId,
                "status" to status,
                "progress" to 0 // Default progress
            )

            val response = executeAuthenticatedQuery(AniListQueries.MEDIA_LIST_MUTATION, variables)
            if (response.isSuccessful) {
                Log.d(TAG, "Added animeId: $animeId to media list with status: $status")
                return@withContext true
            } else {
                Log.e(TAG, "Failed to add to media list: ${response.errorBody()?.string()}")
                return@withContext false
            }
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
            if (token == null) {
                Log.w(TAG, "No token available for markEpisodeAsWatched")
                return@withContext false
            }

            val variables = mapOf(
                "mediaId" to animeId,
                "progress" to episodeNumber
            )

            // Mutation to update media list entry progress
            val query = """
                mutation (${'$'}mediaId: Int, ${'$'}progress: Int) {
                  SaveMediaListEntry(mediaId: ${'$'}mediaId, progress: ${'$'}progress) {
                    id
                    mediaId
                    progress
                    status
                  }
                }
            """.trimIndent()

            val response = executeAuthenticatedQuery(query, variables)
            if (response.isSuccessful) {
                Log.d(TAG, "Marked episode $episodeNumber as watched for animeId: $animeId")
                return@withContext true
            } else {
                Log.e(TAG, "Failed to mark episode as watched: ${response.errorBody()?.string()}")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error marking episode as watched", e)
            return@withContext false
        }
    }

    /**
     * Check if user is authenticated
     */
    fun isUserAuthenticated(): Boolean {
        return token != null
    }
}