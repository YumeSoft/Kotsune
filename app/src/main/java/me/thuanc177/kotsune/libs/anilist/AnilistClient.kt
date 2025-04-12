package me.thuanc177.kotsune.libs.anilist

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Client for interacting with the AniList GraphQL API.
 * Uses Retrofit for network operations.
 */
class AnilistClient {
    private var token: String? = null
    private var userId: Int? = null

    /**
     * Set authentication token without logging in
     */
    fun setToken(token: String) {
        this.token = token
    }

    /**
     * Authenticate user and retrieve user information
     */
    suspend fun loginUser(token: String): AnilistTypes.AnilistUser? {
        this.token = token

        val response = executeAuthenticatedQuery(
            AniListQueries.GET_LOGGED_IN_USER_QUERY,
            emptyMap()
        )

        if (!response.isSuccessful || response.body()?.data == null) {
            return null
        }

        val userData = response.body()?.data?.Viewer ?: return null
        userId = userData.id

        return userData
    }

    /**
     * Execute a GraphQL query with authentication if token is available
     */
    private suspend fun executeAuthenticatedQuery(
        query: String,
        variables: Map<String, Any>
    ) = withContext(Dispatchers.IO) {
        val request = GraphqlRequest(query, variables)
        val authHeader = token?.let { "Bearer $it" }
        return@withContext RetrofitClient.service.executeQuery(request, authHeader)
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
                return@withContext Pair(false, null)
            }
        } catch (e: Exception) {
            Log.e("AnilistClient", "Error fetching trending anime", e)
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
                return@withContext Pair(false, null)
            }
        } catch (e: Exception) {
            Log.e("AnilistClient", "Error fetching recent anime", e)
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
                return@withContext Pair(false, null)
            }
        } catch (e: Exception) {
            Log.e("AnilistClient", "Error fetching highly rated anime", e)
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
                Log.e("AnilistClient", "Error response: ${response.errorBody()?.string()}")
                return@withContext Pair(false, null)
            }
        } catch (e: Exception) {
            Log.e("AnilistClient", "Error searching anime", e)
            return@withContext Pair(false, null)
        }
    }

    /**
     * Get detailed information about a specific anime
     */
    suspend fun getAnimeDetailed(id: Int): Pair<Boolean, JSONObject?> = withContext(Dispatchers.IO) {
        try {
            val variables = mapOf("id" to id)

            val response = executeAuthenticatedQuery(AniListQueries.ANIME_INFO_QUERY, variables)
            if (response.isSuccessful && response.body() != null) {
                Log.d("AnilistClient", "Response raw: ${response.raw()}")
                Log.d("AnilistClient", "Response body: ${response.body()}")
                val jsonString = RetrofitClient.gson.toJson(response.body())
                return@withContext Pair(true, JSONObject(jsonString))
            } else {
                return@withContext Pair(false, null)
            }
        } catch (e: Exception) {
            Log.e("AnilistClient", "Error fetching anime details", e)
            return@withContext Pair(false, null)
        }
    }

    /**
     * Toggle favorite status for an anime
     */
    suspend fun toggleFavorite(animeId: Int, favorite: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            if (token == null) return@withContext false // User must be authenticated

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
            return@withContext response.isSuccessful

        } catch (e: Exception) {
            Log.e("AnilistClient", "Error toggling favorite", e)
            return@withContext false
        }
    }

    /**
     * Add anime to user's media list with specific status
     */
    suspend fun addToMediaList(animeId: Int, status: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (token == null) return@withContext false // User must be authenticated

            val variables = mapOf(
                "mediaId" to animeId,
                "status" to status,
                "progress" to 0 // Default progress
            )

            val response = executeAuthenticatedQuery(AniListQueries.MEDIA_LIST_MUTATION, variables)
            return@withContext response.isSuccessful

        } catch (e: Exception) {
            Log.e("AnilistClient", "Error adding to media list", e)
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