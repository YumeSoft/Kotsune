package me.thuanc177.kotsune.libs.mangaProvider.mangadex

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.thuanc177.kotsune.libs.MangaProvider
import me.thuanc177.kotsune.libs.common.searchForMangaWithAnilist
import me.thuanc177.kotsune.libs.common.fetchMangaInfoFromBal
import okhttp3.Request
import org.json.JSONObject

class MangaDexApi : MangaProvider() {
    private val TAG = "MangaDexApi"

    suspend fun searchForManga(title: String, vararg args: Any): List<Map<String, Any>>? = withContext(Dispatchers.IO) {
        try {
            return@withContext searchForMangaWithAnilist(title)
        } catch (e: Exception) {
            Log.e(TAG, "[MANGADEX-ERROR]: ${e.message}")
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