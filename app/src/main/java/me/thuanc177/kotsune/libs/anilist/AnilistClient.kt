package me.thuanc177.kotsune.libs.anilist

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.AnilistUser

class AnilistClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private var token: String? = null
    private var userId: Int? = null
    private var headers: Map<String, String> = emptyMap()

    suspend fun loginUser(token: String): AnilistUser? {
        this.token = token
        this.headers = mapOf("Authorization" to "Bearer $token")

        val (success, userData) = getLoggedInUser()
        if (!success || userData == null) {
            return null
        }

        val userInfo = userData.getJSONObject("data").getJSONObject("Viewer")
        userId = userInfo.getInt("id")

        return AnilistUser(
            id = userId!!,
            name = userInfo.getString("name"),
            avatar = userInfo.optJSONObject("avatar")?.optString("medium") ?: ""
        )
    }

    fun updateLoginInfo(user: AnilistUser, token: String) {
        this.token = token
        this.headers = mapOf("Authorization" to "Bearer $token")
        this.userId = user.id
    }

    suspend fun getUserInfo(): Pair<Boolean, JSONObject?> {
        return makeAuthenticatedRequest(AniListQueries.GET_USER_INFO)
    }

    suspend fun getLoggedInUser(): Pair<Boolean, JSONObject?> {
        if (headers.isEmpty()) {
            return Pair(false, null)
        }
        return makeAuthenticatedRequest(AniListQueries.GET_LOGGED_IN_USER_QUERY)
    }

// TO DO
//    suspend fun updateAnimeList(valuesToUpdate: Map<String, Any>): Pair<Boolean, JSONObject?> {
//        val variables = valuesToUpdate.toMutableMap()
//        variables["userId"] = userId!!
//        return makeAuthenticatedRequest(AniListQueries, variables)
//    }

//    suspend fun getAnimeList(
//        status: String,
//        type: String = "ANIME",
//        page: Int = 1,
//        perPage: Int = 25
//    ): Pair<Boolean, JSONObject?> {
//        val variables = mapOf(
//            "status" to status,
//            "userId" to userId!!,
//            "type" to type,
//            "page" to page,
//            "perPage" to perPage
//        )
//        return makeAuthenticatedRequest(AniListQueries, variables)
//    }

    private suspend fun makeAuthenticatedRequest(
        query: String,
        variables: Map<String, Any> = emptyMap()
    ): Pair<Boolean, JSONObject?> = withContext(Dispatchers.IO) {
        try {
            val jsonVariables = JSONObject(variables).toString()
            val jsonBody = JSONObject().apply {
                put("query", query)
                put("variables", JSONObject(variables))
            }.toString()

            val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(AnilistConstants.ANILIST_ENDPOINT)
                .post(requestBody)
                .apply {
                    headers.forEach { (key, value) ->
                        addHeader(key, value)
                    }
                }
                .build()

            val response = client.newCall(request).execute()

            val remainingRequests = response.header("X-RateLimit-Remaining")?.toIntOrNull() ?: 0
            if (remainingRequests < 30 && response.code != 500) {
                Log.w("AniList", "Warning: exceeding allowed number of calls per minute")
            }

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                val jsonObject = responseBody?.let { JSONObject(it) }
                return@withContext Pair(true, jsonObject)
            } else {
                val responseBody = response.body?.string()
                val jsonObject = responseBody?.let { JSONObject(it) }
                return@withContext Pair(false, jsonObject)
            }
        } catch (e: IOException) {
            Log.e("AniList", "Connection error: ${e.message}")
            return@withContext Pair(false, null)
        } catch (e: Exception) {
            Log.e("AniList", "Unexpected error: ${e.message}")
            return@withContext Pair(false, null)
        }
    }

    suspend fun getData(
        query: String,
        variables: Map<String, Any> = emptyMap()
    ): Pair<Boolean, JSONObject?> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = JSONObject().apply {
                put("query", query)
                put("variables", JSONObject(variables))
            }.toString()

            val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(AnilistConstants.ANILIST_ENDPOINT)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()

            val remainingRequests = response.header("X-RateLimit-Remaining")?.toIntOrNull() ?: 0
            if (remainingRequests < 30 && response.code != 500) {
                Log.w("AniList", "Warning: exceeding allowed number of calls per minute")
            }

            if (response.isSuccessful) {
                val responseBody = response.body.string()
                val jsonObject = responseBody.let { JSONObject(it) }
                return@withContext Pair(true, jsonObject)
            } else {
                val responseBody = response.body.string()
                val jsonObject = responseBody.let {
                    JSONObject().put("Error", "API request failed with code ${response.code}")
                }
                return@withContext Pair(false, jsonObject)
            }
        } catch (e: IOException) {
            Log.e("AniList", "Connection error: ${e.message}")
            val errorJson = JSONObject().put("Error", "Connection error: ${e.message}")
            return@withContext Pair(false, errorJson)
        } catch (e: Exception) {
            Log.e("AniList", "Unexpected error: ${e.message}")
            val errorJson = JSONObject().put("Error", "${e.message}")
            return@withContext Pair(false, errorJson)
        }
    }

    suspend fun search(
        maxResults: Int = 50,
        query: String? = null,
        sort: List<String>? = null,
        genreIn: List<String>? = null,
        genreNotIn: List<String> = listOf("hentai"),
        type: String = "ANIME",
        page: Int? = null,
        vararg additionalParams: Pair<String, Any?>,
        status_not: String
    ): Pair<Boolean, JSONObject?> {
        val variables = mutableMapOf<String, Any>(
            "maxResults" to maxResults,
            "type" to type
        )

        query?.let { variables["query"] = it }
        sort?.let { variables["sort"] = it }
        genreIn?.let { if (it.isNotEmpty()) variables["genre_in"] = it }
        genreNotIn.let { if (it.isNotEmpty()) variables["genre_not_in"] = it }
        page?.let { variables["page"] = it }

        additionalParams.forEach { (key, value) ->
            if (value != null) variables[key] = value
        }

        return getData(AniListQueries.SEARCH_QUERY, variables)
    }

    suspend fun getAnimeInfo(id: Int): Pair<Boolean, JSONObject?> {
        return getData(AniListQueries.ANIME_INFO_QUERY, mapOf("id" to id))
    }

    suspend fun getRecentAnime(type: String = "ANIME"): Pair<Boolean, JSONObject?> {
        val variables = mapOf(
            "type" to type,
        )
        return getData(AniListQueries.MOST_RECENTLY_UPDATED_QUERY, variables)
    }

    suspend fun getTrendingAnime(
        type: String = "ANIME",
        page: Int = 1,
        perPage: Int = 10
    ): Pair<Boolean, JSONObject?> {
        val variables = mapOf(
            "type" to type,
            "page" to page,
            "perPage" to perPage
        )
        return getData(AniListQueries.TRENDING_QUERY, variables)
    }

    suspend fun getHighlyRatedAnime(
        type: String = "ANIME",
        page: Int = 1,
        perPage: Int = 30
    ): Pair<Boolean, JSONObject?> {
        val variables = mapOf(
            "type" to type,
            "page" to page,
            "perPage" to perPage
        )
        return getData(AniListQueries.MOST_SCORED_QUERY, variables)
    }
}