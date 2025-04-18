package me.thuanc177.kotsune.libs.mangaProvider.mangadex

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.thuanc177.kotsune.libs.MangaProvider
import me.thuanc177.kotsune.libs.common.searchForMangaWithAnilist
import me.thuanc177.kotsune.libs.common.fetchMangaInfoFromBal
import okhttp3.Request
import org.json.JSONArray
import java.net.URL
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MangaDexAPI : MangaProvider() {
    private val TAG = "MangaDexApi"
    private val baseUrl = "https://api.mangadex.org"

    suspend fun getPopularManga(limit: Int = 10): Pair<Boolean, JSONObject?> = withContext(Dispatchers.IO) {
        suspendCoroutine { continuation ->
            try {
                val url = URL("$baseUrl/manga?limit=$limit&order[followedCount]=desc&includes[]=cover_art")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(response)
                    continuation.resume(Pair(true, jsonObject))
                } else {
                    Log.e(TAG, "Failed to get popular manga, response code: $responseCode")
                    continuation.resume(Pair(false, null))
                }

                connection.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting popular manga: ${e.message}", e)
                continuation.resume(Pair(false, null))
            }
        }
    }

    suspend fun getLatestMangaUpdates(
        limit: Int = 50,
        offset: Int = 0
    ): Pair<Boolean, JSONObject?> = withContext(Dispatchers.IO) {
        suspendCoroutine { continuation ->
            try {
                val url = URL("$baseUrl/manga?limit=$limit&offset=$offset&order[updatedAt]=desc&includes[]=cover_art")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(response)
                    continuation.resume(Pair(true, jsonObject))
                } else {
                    Log.e(TAG, "Failed to get latest manga updates, response code: $responseCode")
                    continuation.resume(Pair(false, null))
                }
                connection.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting latest manga updates: ${e.message}", e)
                continuation.resume(Pair(false, null))
            }
        }
    }



//    suspend fun searchForManga(title: String, vararg args: Any): List<Map<String, Any>>? = withContext(Dispatchers.IO) {
//        try {
//            return@withContext searchForMangaWithAnilist(title)
//        } catch (e: Exception) {
//            Log.e(TAG, "[MANGADEX-ERROR]: ${e.message}")
//            return@withContext null
//        }
//    }

    suspend fun searchForManga(title: String, vararg args: Any): List<Map<String, Any>>? = withContext(Dispatchers.IO) {
        try {
            val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
            val url =
                URL("$baseUrl/manga?title=$encodedTitle&limit=20&includes[]=cover_art&includes[]=tag")

            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(response)
                val dataArray = jsonObject.getJSONArray("data")
                val results = mutableListOf<Map<String, Any>>()

                for (i in 0 until dataArray.length()) {
                    val mangaObject = dataArray.getJSONObject(i)
                    val mangaId = mangaObject.getString("id")
                    val attributes = mangaObject.getJSONObject("attributes")

                    // Get title
                    val titleObj = attributes.getJSONObject("title")
                    val title = titleObj.optString("en") ?: titleObj.keys().asSequence()
                        .map { titleObj.getString(it) }.firstOrNull() ?: "Unknown"

                    // Get status
                    val status = attributes.optString("status", "unknown")

                    // Get year from publishAt date
                    var year: Int? = null
                    if (attributes.has("year")) {
                        year = attributes.optInt("year")
                    } else if (attributes.has("publicationDemographic")) {
                        // Try to get year from created date as fallback
                        attributes.optString("createdAt", "")
                            .substringBefore("T")
                            .substringBefore("-")
                            .toIntOrNull()?.let { year = it }
                    }

                    // Get rating
                    val rating = attributes.optJSONObject("contentRating")?.let {
                        when (it.toString()) {
                            "safe" -> 0f
                            "suggestive" -> 1f
                            "erotica" -> 2f
                            "pornographic" -> 3f
                            else -> null
                        }
                    } ?: attributes.optString("rating", "").toFloatOrNull()

                    // Get cover art
                    var coverImage: String? = null
                    val relationships = mangaObject.optJSONArray("relationships") ?: JSONArray()
                    for (j in 0 until relationships.length()) {
                        val rel = relationships.getJSONObject(j)
                        if (rel.getString("type") == "cover_art") {
                            val coverAttributes = rel.getJSONObject("attributes")
                            val fileName = coverAttributes.optString("fileName")
                            if (fileName.isNotEmpty()) {
                                coverImage =
                                    "https://uploads.mangadex.org/covers/$mangaId/$fileName"
                                break
                            }
                        }
                    }
                    if (coverImage == null) {
                        Log.w(TAG, "No cover art found for manga ID: $mangaId")
                    }

                    // Get tags
                    val tags = mutableListOf<String>()
                    val tagsArray = attributes.getJSONArray("tags")
                    for (j in 0 until tagsArray.length()) {
                        val tag = tagsArray.getJSONObject(j)
                        val tagAttributes = tag.getJSONObject("attributes")
                        val tagName = tagAttributes.getJSONObject("name")
                        tagName.optString("en")?.let { tags.add(it) }
                    }

                    val mangaMap = mapOf(
                        "id" to mangaId,
                        "title" to title,
                        "poster" to (coverImage ?: ""), // Use empty string instead of null
                        "type" to status,
                        "genres" to tags,
                        "year" to year,
                        "rating" to rating
                    )

                    results.add(mangaMap as Map<String, Any>)
                }

                connection.disconnect()
                Log.d(TAG, "Search found ${results.size} manga")
                return@withContext results
            } else {
                Log.e(TAG, "Failed to search manga, response code: $responseCode")
                connection.disconnect()
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching manga: ${e.message}", e)
            return@withContext null
        }
    }

    suspend fun getManga(anilistMangaId: String): Map<String, Any?>? = withContext(Dispatchers.IO) {
        val balData = fetchMangaInfoFromBal(anilistMangaId) ?: return@withContext null

        val sitesObj = balData.getJSONObject("Sites")
        val mangadexObj = sitesObj.getJSONObject("Mangadex")
        val mangaId = mangadexObj.keys().next()
        val mangaDexManga = mangadexObj.getJSONObject(mangaId)

        return@withContext mapOf(
            "id" to mangaId,
            "title" to mangaDexManga.getString("title"),
            "poster" to mangaDexManga.getString("image"),
            "availableChapters" to emptyList<String>()
        )
    }

    suspend fun getChapterThumbnails(mangaId: String, chapter: String): Map<String, Any>? = withContext(Dispatchers.IO) {
        try {
            val chapterInfoUrl = "https://api.mangadex.org/chapter?manga=$mangaId&translatedLanguage[]=en&chapter=$chapter&includeEmptyPages=0"
            val chapterInfoRequest = Request.Builder().url(chapterInfoUrl).build()

            val chapterInfoResponse = client.newCall(chapterInfoRequest).execute()
            if (!chapterInfoResponse.isSuccessful) return@withContext null

            val chapterInfoJson = JSONObject(chapterInfoResponse.body?.string() ?: "")
            val dataArray = chapterInfoJson.getJSONArray("data")
            if (dataArray.length() == 0) return@withContext null

            val chapterInfo = dataArray.getJSONObject(0)
            val chapterId = chapterInfo.getString("id")

            val thumbnailsUrl = "https://api.mangadex.org/at-home/server/$chapterId"
            val thumbnailsRequest = Request.Builder().url(thumbnailsUrl).build()
            val thumbnailsResponse = client.newCall(thumbnailsRequest).execute()

            if (!thumbnailsResponse.isSuccessful) return@withContext null

            val thumbnailsJson = JSONObject(thumbnailsResponse.body?.string() ?: "")
            val baseUrl = thumbnailsJson.getString("baseUrl")
            val chapterObj = thumbnailsJson.getJSONObject("chapter")
            val hash = chapterObj.getString("hash")

            val thumbnails = ArrayList<String>()
            for (i in 0 until dataArray.length()) {
                thumbnails.add("$baseUrl/data/$hash/${dataArray.getString(i)}")
            }

            val attributes = chapterInfo.getJSONObject("attributes")
            val title = attributes.getString("title")

            return@withContext mapOf(
                "thumbnails" to thumbnails,
                "title" to title
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting chapter thumbnails: ${e.message}")
            return@withContext null
        }
    }
}