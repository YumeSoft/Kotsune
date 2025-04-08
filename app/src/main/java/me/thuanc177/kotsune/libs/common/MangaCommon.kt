package me.thuanc177.kotsune.libs.common

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit


private const val TAG = "MangaCommon"

suspend fun fetchMangaInfoFromBal(anilistId: String): JSONObject? = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient.Builder()
            .connectTimeout(11, TimeUnit.SECONDS)
            .build()

        val url = "https://raw.githubusercontent.com/bal-mackup/mal-backup/master/anilist/manga/$anilistId.json"
        val request = Request.Builder().url(url).build()

        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            val jsonString = response.body.string()
            return@withContext JSONObject(jsonString)
        }
        return@withContext null
    } catch (e: Exception) {
        Log.e(TAG, "Error fetching manga info: ${e.message}")
        return@withContext null
    }
}