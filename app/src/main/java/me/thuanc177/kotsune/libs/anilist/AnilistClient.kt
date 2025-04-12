package me.thuanc177.kotsune.libs.anilist

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class AnilistClient {
    private var token: String? = null
    private var userId: Int? = null

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

    private suspend fun executeAuthenticatedQuery(
        query: String,
        variables: Map<String, Any>
    ) = withContext(Dispatchers.IO) {
        val request = GraphqlRequest(query, variables)
        val authHeader = token?.let { "Bearer $it" }
        return@withContext RetrofitClient.service.executeQuery(request, authHeader)
    }

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
                // Convert to JSONObject for backward compatibility
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
}