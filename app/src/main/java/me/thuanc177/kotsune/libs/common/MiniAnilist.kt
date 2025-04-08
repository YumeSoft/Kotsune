package me.thuanc177.kotsune.libs.common

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "MiniAniList"
private const val ANILIST_ENDPOINT = "https://graphql.anilist.co"

/**
 * Search for manga using the AniList GraphQL API
 */
suspend fun searchForMangaWithAnilist(mangaTitle: String): List<Map<String, Any>>? = withContext(Dispatchers.IO) {
    try {
        val query = """
            query (${'$'}query: String) {
                Page(perPage: 50) {
                    pageInfo {
                        currentPage
                    }
                    media(search: ${'$'}query, type: MANGA, genre_not_in: ["hentai"]) {
                        id
                        idMal
                        title {
                            english
                            romaji
                            native
                        }
                        chapters
                        status
                        coverImage {
                            medium
                            large
                        }
                    }
                }
            }
        """

        val variables = JSONObject().put("query", mangaTitle)
        val requestBody = JSONObject()
            .put("query", query)
            .put("variables", variables)
            .toString()
            .toRequestBody("application/json".toMediaType())

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(ANILIST_ENDPOINT)
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            val responseData = JSONObject(response.body?.string() ?: "")
            val pageData = responseData.getJSONObject("data").getJSONObject("Page")
            val mediaArray = pageData.getJSONArray("media")

            val results = mutableListOf<Map<String, Any>>()

            for (i in 0 until mediaArray.length()) {
                val animeResult = mediaArray.getJSONObject(i)
                val id = animeResult.getInt("id")
                val coverImage = animeResult.getJSONObject("coverImage")
                val title = animeResult.getJSONObject("title")

                val chapters = if (!animeResult.isNull("chapters"))
                    animeResult.getInt("chapters") else 0

                val availableChapters = mutableListOf<Int>()
                if (chapters > 0) {
                    for (j in 1 until chapters) {
                        availableChapters.add(j)
                    }
                }

                val titleText = (title.optString("romaji")
                    ?: title.optString("english", "")) +
                        "  [Chapters: $chapters]"

                results.add(mapOf(
                    "id" to id,
                    "poster" to coverImage.getString("large"),
                    "title" to titleText,
                    "type" to "manga",
                    "availableChapters" to availableChapters
                ))
            }

            return@withContext results
        }
        return@withContext null
    } catch (e: Exception) {
        Log.e(TAG, "Error searching for manga: ${e.message}")
        return@withContext null
    }
}

/**
 * Search for anime using the AniList GraphQL API
 */
suspend fun searchForAnimeWithAnilist(
    animeTitle: String,
    preferEngTitles: Boolean = false
): Map<String, Any>? = withContext(Dispatchers.IO) {
    try {
        val query = """
            query (${'$'}query: String) {
              Page(perPage: 50) {
                pageInfo {
                  total
                  currentPage
                  hasNextPage
                }
                media(search: ${'$'}query, type: ANIME, genre_not_in: ["hentai"]) {
                  id
                  idMal
                  title {
                    romaji
                    english
                  }
                  episodes
                  status
                  synonyms
                  nextAiringEpisode {
                    timeUntilAiring
                    airingAt
                    episode
                  }
                  coverImage {
                    large
                  }
                }
              }
            }
        """

        // Implementation similar to searchForMangaWithAnilist
        // Simplified for brevity - expand as needed

        return@withContext null
    } catch (e: Exception) {
        Log.e(TAG, "Error searching for anime: ${e.message}")
        return@withContext null
    }
}