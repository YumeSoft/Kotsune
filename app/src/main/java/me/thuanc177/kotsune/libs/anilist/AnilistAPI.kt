package me.thuanc177.kotsune.libs.anilist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import me.thuanc177.kotsune.libs.anilist.AnilistTypes.AnilistResponse

class AniListAPI {
    private val client = OkHttpClient()
    private val endpoint = "https://graphql.anilist.co"
    private val mediaType = "application/json; charset=utf-8".toMediaType()
    private var token: String? = null
    private var userId: Int? = null

    fun setToken(token: String) {
        this.token = token
    }

    suspend fun search(
        query: String,
        page: Int = 1,
        perPage: Int = 20,
        type: String = "ANIME"
    ): Result<AnilistResponse> = withContext(Dispatchers.IO) {
        val variables = mapOf(
            "query" to query,
            "page" to page,
            "perPage" to perPage,
            "type" to type
        )
        return@withContext executeQuery(AniListQueries.SEARCH_QUERY, variables.toJsonObject())
    }

    private fun Map<String, Any>.toJsonObject(): JSONObject {
        val jsonObject = JSONObject()
        this.forEach { (key, value) -> jsonObject.put(key, value) }
        return jsonObject
    }

    suspend fun getTrendingAnime(
        page: Int = 1,
        perPage: Int = 20
    ): Result<AnilistResponse> = withContext(Dispatchers.IO) {
        val variables = JSONObject().apply {
            put("page", page)
            put("perPage", perPage)
            put("type", "ANIME")
        }
        return@withContext executeQuery(AniListQueries.TRENDING_QUERY, variables)
    }

    suspend fun getRecentAnime(
        page: Int = 1,
        perPage: Int = 20
    ): Result<AnilistResponse> = withContext(Dispatchers.IO) {
        val variables = JSONObject().apply {
            put("page", page)
            put("perPage", perPage)
            put("type", "ANIME")
        }
        return@withContext executeQuery(AniListQueries.MOST_RECENTLY_UPDATED_QUERY, variables)
    }

    suspend fun getAnime(id: Int): Result<AnilistResponse> = withContext(Dispatchers.IO) {
        val variables = JSONObject().apply {
            put("id", id)
        }
        return@withContext executeQuery(AniListQueries.ANIME_INFO_QUERY, variables)
    }

    private suspend fun executeQuery(
        query: String,
        variables: JSONObject
    ): Result<AnilistResponse> = withContext(Dispatchers.IO) {
        try {
            val requestBody = JSONObject().apply {
                put("query", query)
                put("variables", variables)
            }.toString().toRequestBody(mediaType)

            val requestBuilder = Request.Builder()
                .url(endpoint)
                .post(requestBody)

            token?.let {
                requestBuilder.addHeader("Authorization", "Bearer $it")
            }

            val request = requestBuilder.build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("HTTP error ${response.code}"))
            }

            val responseBody = response.body?.string() ?: return@withContext Result.failure(
                IOException("Empty response body")
            )

            // Parse the JSON here using a library like Gson or Moshi
            // For simplicity, let's assume we have a parseResponse function
            return@withContext Result.success(parseResponse(responseBody))
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    // This would use a proper JSON parsing library in actual code
    private fun parseResponse(json: String): AnilistResponse {
        // Implementation would use Gson/Moshi in a real app
        // Simplified for example purposes
        return AnilistResponse()
    }
}