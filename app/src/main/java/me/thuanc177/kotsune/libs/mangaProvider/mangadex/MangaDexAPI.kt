package me.thuanc177.kotsune.libs.mangaProvider.mangadex

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.thuanc177.kotsune.config.AppConfig
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.ChapterModel
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.Manga
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.MangaStatistics
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.MangaTag
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.MangaWithStatus
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.RatingStatistics
import me.thuanc177.kotsune.libs.mangaProvider.mangadex.MangaDexTypes.ReadingHistoryItem
import org.json.JSONArray
import java.net.URL
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URLEncoder
import kotlin.Boolean
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MangaDexAPI (
    private val appConfig: AppConfig
) {
    private val TAG = "MangaDexAPI"
    private val baseUrl = "https://api.mangadex.org"
    private val authUrl = "https://auth.mangadex.org/realms/mangadex/protocol/openid-connect/token"

    // Token synchronization object to prevent multiple simultaneous refresh attempts
    private val tokenRefreshLock = Any()
    private var isRefreshing = false

    /**
     * Refreshes the access token using refresh token
     * @return True if token was successfully refreshed
     */
    private suspend fun refreshToken(): Boolean = withContext(Dispatchers.IO) {
        // Use synchronization to prevent multiple refresh attempts at once
        synchronized(tokenRefreshLock) {
            if (isRefreshing) {
                // Wait for the ongoing refresh to complete
                while (isRefreshing) {
                    Thread.sleep(100)
                }
                return@withContext appConfig.hasValidMangadexToken()
            }

            isRefreshing = true
        }

        try {
            val refreshToken = appConfig.mangadexRefreshToken
            if (refreshToken.isBlank()) {
                Log.e(TAG, "Refresh token is empty")
                isRefreshing = false
                return@withContext false
            }

            val clientId = appConfig.mangadexClientId
            val clientSecret = appConfig.mangadexClientSecret

            val url = URL(authUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

            // Prepare form data
            val formData = buildString {
                append("grant_type=refresh_token")
                append("&refresh_token=${URLEncoder.encode(refreshToken, "UTF-8")}")
                append("&client_id=${URLEncoder.encode(clientId, "UTF-8")}")
                append("&client_secret=${URLEncoder.encode(clientSecret, "UTF-8")}")
            }

            // Send request
            val outputWriter = OutputStreamWriter(connection.outputStream)
            outputWriter.write(formData)
            outputWriter.flush()
            outputWriter.close()

            val responseCode = connection.responseCode
            Log.d(TAG, "Refresh token response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Parse the response
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(response)

                // Get tokens (a refresh may also return a new refresh token)
                val accessToken = jsonObject.getString("access_token")
                val newRefreshToken = jsonObject.optString("refresh_token", refreshToken)
                val expiresIn = jsonObject.optInt("expires_in", 900) // Default to 15 minutes

                // Calculate token expiry time (current time + expires_in seconds)
                val expiryTime = System.currentTimeMillis() + (expiresIn * 1000)

                // Save new tokens
                appConfig.mangadexAccessToken = accessToken
                appConfig.mangadexRefreshToken = newRefreshToken
                appConfig.mangadexTokenExpiry = expiryTime

                Log.d(TAG, "Token refreshed successfully, valid for $expiresIn seconds")
                return@withContext true
            } else {
                // Handle error response
                val errorStream = connection.errorStream
                val errorResponse = errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e(TAG, "Error refreshing token: $errorResponse")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing token", e)
            return@withContext false
        } finally {
            isRefreshing = false
        }
    }

    /**
     * Helper function to execute API requests with automatic token refresh on 401 responses
     * @param apiCall The API call to execute, which returns the HttpURLConnection
     * @return HttpURLConnection with the established connection
     */
    private suspend fun executeWithTokenRefresh(
        apiCall: () -> HttpURLConnection
    ): HttpURLConnection = withContext(Dispatchers.IO) {
        var connection = apiCall()
        var responseCode = connection.responseCode

        // If unauthorized and we have refresh token, try refreshing and retry the request
        if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
            Log.d(TAG, "Received 401 Unauthorized, attempting token refresh")
            if (refreshToken()) {
                // Disconnect the failed connection
                connection.disconnect()

                // Retry with new token
                connection = apiCall()
                responseCode = connection.responseCode

                if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    Log.e(TAG, "Still unauthorized after token refresh")
                }
            } else {
                Log.e(TAG, "Token refresh failed")
            }
        }

        return@withContext connection
    }

    /**
     * Checks if the current user is authenticated with MangaDex
     * @return boolean indicating if there's a valid token
     */
    fun isAuthenticated(): Boolean {
        return appConfig.hasValidMangadexToken()
    }

    /**
     * Create or update a rating for a manga
     * @param mangaId The MangaDex manga ID
     * @param rating Rating value from 1-10
     * @return Boolean indicating success
     */
    suspend fun rateManga(mangaId: String, rating: Int): Boolean = withContext(Dispatchers.IO) {
        if (!isAuthenticated()) {
            Log.d(TAG, "rateManga: Not authenticated")
            return@withContext false
        }

        try {
            // Validate rating
            if (rating < 1 || rating > 10) {
                Log.e(TAG, "Invalid rating value: $rating. Must be between 1-10")
                return@withContext false
            }

            val connection = executeWithTokenRefresh {
                val url = URL("$baseUrl/rating/$mangaId")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer ${appConfig.mangadexAccessToken}")

                // Create JSON body
                val jsonBody = JSONObject().apply {
                    put("rating", rating)
                }

                // Write output
                val outputWriter = OutputStreamWriter(conn.outputStream)
                outputWriter.write(jsonBody.toString())
                outputWriter.flush()
                outputWriter.close()

                conn
            }

            val responseCode = connection.responseCode
            Log.d(TAG, "Rate manga response code: $responseCode")

            val success = responseCode == HttpURLConnection.HTTP_OK
            connection.disconnect()
            return@withContext success
        } catch (e: Exception) {
            Log.e(TAG, "Error rating manga", e)
            return@withContext false
        }
    }

    /**
     * Delete a rating for a manga
     * @param mangaId The MangaDex manga ID
     * @return Boolean indicating success
     */
    suspend fun deleteRating(mangaId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isAuthenticated()) {
            Log.d(TAG, "deleteRating: Not authenticated")
            return@withContext false
        }

        try {
            val connection = executeWithTokenRefresh {
                val url = URL("$baseUrl/rating/$mangaId")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "DELETE"
                conn.setRequestProperty("Authorization", "Bearer ${appConfig.mangadexAccessToken}")
                conn
            }

            val responseCode = connection.responseCode
            Log.d(TAG, "Delete rating response code: $responseCode")

            val success = responseCode == HttpURLConnection.HTTP_OK
            connection.disconnect()
            return@withContext success
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting manga rating", e)
            return@withContext false
        }
    }

    /**
     * Get read chapters for a specific manga
     * @param mangaId The MangaDex manga ID
     * @return List of chapter IDs that have been read, or null if error
     */
    suspend fun getMangaReadMarkers(mangaId: String): List<String>? = withContext(Dispatchers.IO) {
        if (!isAuthenticated()) {
            Log.d(TAG, "getMangaReadMarkers: Not authenticated")
            return@withContext null
        }

        try {
            val connection = executeWithTokenRefresh {
                val url = URL("$baseUrl/manga/$mangaId/read")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer ${appConfig.mangadexAccessToken}")
                conn
            }

            val responseCode = connection.responseCode
            Log.d(TAG, "Get manga read markers response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(response)

                if (jsonObject.getString("result") == "ok") {
                    val dataArray = jsonObject.getJSONArray("data")
                    val chapterIds = mutableListOf<String>()

                    for (i in 0 until dataArray.length()) {
                        chapterIds.add(dataArray.getString(i))
                    }

                    return@withContext chapterIds
                }
            }

            connection.disconnect()
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting manga read markers", e)
            return@withContext null
        }
    }

    /**
     * Batch update read markers for a manga (mark chapters as read or unread)
     * @param mangaId The MangaDex manga ID
     * @param readChapterIds List of chapter IDs to mark as read
     * @param unreadChapterIds List of chapter IDs to mark as unread
     * @return Boolean indicating success
     */
    suspend fun batchUpdateReadMarkers(
        mangaId: String,
        readChapterIds: List<String> = emptyList(),
        unreadChapterIds: List<String> = emptyList()
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isAuthenticated()) {
            Log.d(TAG, "batchUpdateReadMarkers: Not authenticated")
            return@withContext false
        }

        if (readChapterIds.isEmpty() && unreadChapterIds.isEmpty()) {
            Log.d(TAG, "batchUpdateReadMarkers: No chapters provided to update")
            return@withContext true  // Nothing to do
        }

        try {
            val connection = executeWithTokenRefresh {
                val url = URL("$baseUrl/manga/$mangaId/read")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer ${appConfig.mangadexAccessToken}")

                // Create JSON body
                val jsonBody = JSONObject().apply {
                    if (readChapterIds.isNotEmpty()) {
                        put("chapterIdsRead", JSONArray(readChapterIds))
                    }
                    if (unreadChapterIds.isNotEmpty()) {
                        put("chapterIdsUnread", JSONArray(unreadChapterIds))
                    }
                }

                // Write output
                val outputWriter = OutputStreamWriter(conn.outputStream)
                outputWriter.write(jsonBody.toString())
                outputWriter.flush()
                outputWriter.close()

                conn
            }

            val responseCode = connection.responseCode
            Log.d(TAG, "Batch update read markers response code: $responseCode")

            val success = responseCode == HttpURLConnection.HTTP_OK
            connection.disconnect()
            return@withContext success
        } catch (e: Exception) {
            Log.e(TAG, "Error batch updating read markers", e)
            return@withContext false
        }
    }

    /**
     * Get read markers for multiple manga IDs
     * @param mangaIds List of manga IDs to get read markers for
     * @return Map of manga IDs to lists of read chapter IDs, or null if error
     */
    suspend fun getMultipleMangaReadMarkers(mangaIds: List<String>): Map<String, List<String>>? = withContext(Dispatchers.IO) {
        if (!isAuthenticated()) {
            Log.d(TAG, "getMultipleMangaReadMarkers: Not authenticated")
            return@withContext null
        }

        if (mangaIds.isEmpty()) {
            return@withContext emptyMap()
        }

        try {
            val queryParams = mangaIds.joinToString("&ids[]=", prefix = "?ids[]=")
            val connection = executeWithTokenRefresh {
                val url = URL("$baseUrl/manga/read$queryParams")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer ${appConfig.mangadexAccessToken}")
                conn
            }

            val responseCode = connection.responseCode
            Log.d(TAG, "Get multiple manga read markers response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(response)

                if (jsonObject.getString("result") == "ok") {
                    val dataObject = jsonObject.getJSONObject("data")
                    val resultMap = mutableMapOf<String, List<String>>()

                    for (mangaId in dataObject.keys()) {
                        val chapterArray = dataObject.getJSONArray(mangaId)
                        val chapterIds = mutableListOf<String>()

                        for (i in 0 until chapterArray.length()) {
                            chapterIds.add(chapterArray.getString(i))
                        }

                        resultMap[mangaId] = chapterIds
                    }

                    return@withContext resultMap
                }
            }

            connection.disconnect()
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting multiple manga read markers", e)
            return@withContext null
        }
    }

    /**
     * Get statistics for a manga
     * @param mangaId The MangaDex manga ID
     * @return The manga statistics or null if error
     */
    suspend fun getMangaStatistics(mangaId: String): MangaStatistics? = withContext(Dispatchers.IO) {
        val url = "$baseUrl/statistics/manga/$mangaId"
        val connection = URL(url).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(response)

            if (jsonObject.getString("result") == "ok") {
                val statsObj = jsonObject.getJSONObject("statistics").getJSONObject(mangaId)

                // Get rating information
                val ratingObj = statsObj.getJSONObject("rating")
                val distributionObj = ratingObj.getJSONObject("distribution")
                val distribution = mutableMapOf<Int, Int>()

                // Parse distribution
                for (i in 1..10) {
                    if (distributionObj.has(i.toString())) {
                        distribution[i] = distributionObj.getInt(i.toString())
                    }
                }

                val ratingStats = RatingStatistics(
                    average = ratingObj.optDouble("average", 0.0),
                    bayesian = ratingObj.optDouble("bayesian", 0.0),
                    distribution = distribution
                )

                // Get comment information
                val commentsObj = statsObj.optJSONObject("comments")
                val threadId = commentsObj?.optString("threadId")
                val repliesCount = commentsObj?.optInt("repliesCount", 0) ?: 0

                return@withContext MangaStatistics(
                    rating = ratingStats,
                    follows = statsObj.optInt("follows", 0),
                    commentCount = repliesCount,
                    threadId = threadId
                )
            }

            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching manga statistics", e)
            return@withContext null
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Get the current reading status for a manga
     * @param mangaId The MangaDex manga ID
     * @return The reading status or null if error/not found
     */
    suspend fun getMangaReadingStatus(mangaId: String): String? = withContext(Dispatchers.IO) {
        val url = "$baseUrl/manga/$mangaId/status"
        val connection = URL(url).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer ${appConfig.mangadexAccessToken}")

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(response)

                if (jsonObject.getString("result") == "ok") {
                    return@withContext jsonObject.optString("status")
                }
            }

            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching manga reading status", e)
            return@withContext null
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Update the reading status for a manga
     * @param mangaId The MangaDex manga ID
     * @param status The reading status to set ("reading", "on_hold", "plan_to_read", "completed", "re_reading", "dropped", or null to remove)
     * @return True if successful, false otherwise
     */
    suspend fun updateMangaReadingStatus(mangaId: String, status: String?): Boolean = withContext(Dispatchers.IO) {
        val url = "$baseUrl/manga/$mangaId/status"
        val connection = URL(url).openConnection() as HttpURLConnection

        try {
            connection.setRequestProperty("Authorization", "Bearer ${appConfig.mangadexAccessToken}")
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")

            val json = if (status == null) {
                "{\"status\": null}"
            } else {
                "{\"status\": \"$status\"}"
            }

            connection.outputStream.use { os ->
                val input = json.toByteArray(charset("utf-8"))
                os.write(input, 0, input.size)
            }

            val responseCode = connection.responseCode
            return@withContext responseCode == 200
        } catch (e: Exception) {
            Log.e(TAG, "Error updating manga reading status", e)
            return@withContext false
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Get user's reading history
     * @return List of reading history items or null if failed/unauthorized
     */
    suspend fun getUserReadingHistory(): List<ReadingHistoryItem>? = withContext(Dispatchers.IO) {
        val url = "$baseUrl/user/history"
        val connection = URL(url).openConnection() as HttpURLConnection

        try {
            connection.setRequestProperty("Authorization", "Bearer ${appConfig.mangadexAccessToken}")
            connection.requestMethod = "GET"

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(response)

            if (jsonObject.getString("result") == "ok") {
                val historyArray = jsonObject.getJSONArray("ratings")
                val historyItems = mutableListOf<ReadingHistoryItem>()

                for (i in 0 until historyArray.length()) {
                    val item = historyArray.getJSONObject(i)
                    historyItems.add(
                        ReadingHistoryItem(
                            chapterId = item.getString("chapterId"),
                            readDate = item.getString("readDate")
                        )
                    )
                }
                return@withContext historyItems
            }

            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching reading history", e)
            return@withContext null
        } finally {
            connection.disconnect()
        }
    }
    /**
     * Get user profile information with automatic token refresh
     * @return MangaDexUserProfile or null if error occurs
     */
    suspend fun getUserProfile(): MangaDexTypes.MangaDexUserProfile? = withContext(Dispatchers.IO) {
        if (!isAuthenticated()) {
            Log.d(TAG, "getUserProfile: Not authenticated")
            return@withContext null
        }

        try {
            val connection = executeWithTokenRefresh {
                val url = URL("$baseUrl/user/me")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer ${appConfig.mangadexAccessToken}")
                conn
            }

            val responseCode = connection.responseCode
            Log.d(TAG, "User profile response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(response)

                if (jsonObject.getString("result") == "ok") {
                    val data = jsonObject.getJSONObject("data")
                    val attributes = data.getJSONObject("attributes")
                    val username = attributes.getString("username")
                    val userId = data.getString("id")

                    // Get avatar URL (could be null)
                    var avatarUrl: String? = null
                    try {
                        avatarUrl = attributes.optString("avatar", null)
                    } catch (e: Exception) {
                        // Handle if avatar isn't available
                    }

                    return@withContext MangaDexTypes.MangaDexUserProfile(
                        id = userId,
                        username = username,
                        avatarUrl = avatarUrl
                    )
                }
            }
            connection.disconnect()
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user profile", e)
            return@withContext null
        }
    }

    /**
     * Get user's library (manga with reading status)
     * @return List of MangaWithStatus objects
     */
    suspend fun getUserLibrary(): List<MangaWithStatus> = withContext(Dispatchers.IO) {
        if (!isAuthenticated()) {
            Log.d(TAG, "getUserLibrary: Not authenticated")
            return@withContext emptyList()
        }

        try {
            // First, get user's reading statuses
            val statusUrl = URL("$baseUrl/manga/status")
            val statusConnection = statusUrl.openConnection() as HttpURLConnection
            statusConnection.requestMethod = "GET"
            statusConnection.setRequestProperty("Authorization", "Bearer ${appConfig.mangadexAccessToken}")

            val mangaStatusMap = mutableMapOf<String, String>()
            val statusResponseCode = statusConnection.responseCode

            if (statusResponseCode == HttpURLConnection.HTTP_OK) {
                val response = statusConnection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(response)

                if (jsonObject.getString("result") == "ok") {
                    val statuses = jsonObject.getJSONObject("statuses")
                    val statusKeys = statuses.keys()

                    while (statusKeys.hasNext()) {
                        val mangaId = statusKeys.next()
                        val status = statuses.getString(mangaId)
                        mangaStatusMap[mangaId] = status
                    }
                }
            }

            // Fetch details for each manga in batches
            val mangaWithStatus = mutableListOf<MangaWithStatus>()

            if (mangaStatusMap.isNotEmpty()) {
                val mangaIds = mangaStatusMap.keys.toList()
                val batchSize = 10

                for (i in mangaIds.indices step batchSize) {
                    val batch = mangaIds.subList(i, minOf(i + batchSize, mangaIds.size))
                    val mangaBatch = fetchMangaBatch(batch)

                    for (manga in mangaBatch) {
                        val status = mangaStatusMap[manga.id] ?: continue
                        mangaWithStatus.add(MangaWithStatus(status, manga))
                    }
                }
            }

            return@withContext mangaWithStatus
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user library", e)
            return@withContext emptyList()
        }
    }

    /**
     * Fetch batch of manga details
     * @param ids List of manga IDs to fetch
     * @return List of Manga objects
     */
    private suspend fun fetchMangaBatch(ids: List<String>): List<Manga> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()

        try {
            // Construct URL with ids parameter
            val idParam = ids.joinToString("&ids[]=", prefix = "?ids[]=")
            val urlString = "$baseUrl/manga$idParam&includes[]=cover_art&includes[]=author&includes[]=artist&includes[]=tag"

            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer ${appConfig.mangadexAccessToken}")

            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(response)

                if (jsonObject.getString("result") == "ok") {
                    val data = jsonObject.getJSONArray("data")
                    val mangaList = mutableListOf<Manga>()

                    for (i in 0 until data.length()) {
                        val mangaObject = data.getJSONObject(i)
                        val manga = parseMangaFromJson(mangaObject)
                        mangaList.add(manga)
                    }

                    return@withContext mangaList
                }
            }

            return@withContext emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching manga batch", e)
            return@withContext emptyList()
        }
    }

    /**
     * Update the reading status for a manga
     * @param mangaId The MangaDex manga ID
     * @param status The reading status to set
     * @return Boolean indicating success
     */
    suspend fun updateReadingStatus(mangaId: String, status: String): Boolean = withContext(Dispatchers.IO) {
        if (!isAuthenticated()) {
            Log.d(TAG, "updateReadingStatus: Not authenticated")
            return@withContext false
        }

        try {
            val url = URL("$baseUrl/manga/$mangaId/status")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer ${appConfig.mangadexAccessToken}")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val requestBody = JSONObject().apply {
                put("status", status)
            }

            connection.outputStream.use { os ->
                os.write(requestBody.toString().toByteArray())
            }

            val responseCode = connection.responseCode
            Log.d(TAG, "updateReadingStatus response code: $responseCode")

            return@withContext (responseCode == HttpURLConnection.HTTP_OK)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating reading status", e)
            return@withContext false
        }
    }

    /**
     * Mark a chapter as read
     * @param chapterId The chapter ID to mark as read
     * @return Boolean indicating success
     */
    suspend fun markChapterRead(chapterId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isAuthenticated()) {
            Log.d(TAG, "markChapterRead: Not authenticated")
            return@withContext false
        }

        try {
            val url = URL("$baseUrl/chapter/$chapterId/read")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer ${appConfig.mangadexAccessToken}")

            val responseCode = connection.responseCode
            Log.d(TAG, "markChapterRead response code: $responseCode")

            return@withContext (responseCode == HttpURLConnection.HTTP_OK)
        } catch (e: Exception) {
            Log.e(TAG, "Error marking chapter as read", e)
            return@withContext false
        }
    }

    /**
     * Mark a chapter as unread
     * @param chapterId The chapter ID to mark as unread
     * @return Boolean indicating success
     */
    suspend fun markChapterUnread(chapterId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isAuthenticated()) {
            Log.d(TAG, "markChapterUnread: Not authenticated")
            return@withContext false
        }

        try {
            val url = URL("$baseUrl/chapter/$chapterId/read")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "DELETE"
            connection.setRequestProperty("Authorization", "Bearer ${appConfig.mangadexAccessToken}")

            val responseCode = connection.responseCode
            Log.d(TAG, "markChapterUnread response code: $responseCode")

            return@withContext (responseCode == HttpURLConnection.HTTP_OK)
        } catch (e: Exception) {
            Log.e(TAG, "Error marking chapter as unread", e)
            return@withContext false
        }
    }

    /**
     * Get the user's reading list
     * @param status Optional filter by reading status
     * @param limit Number of results to return (max 100)
     * @param offset Pagination offset
     * @return List of MangaDexTypes.Manga objects
     */
    suspend fun getUserReadingList(
        status: String? = null,
        limit: Int = 20,
        offset: Int = 0
    ): List<Manga> = withContext(Dispatchers.IO) {
        if (!isAuthenticated()) {
            Log.d(TAG, "getUserReadingList: Not authenticated")
            return@withContext emptyList()
        }

        try {
            val statusParam = status?.let { "&status=$it" } ?: ""
            val url = URL("$baseUrl/user/follows/manga?limit=$limit&offset=$offset$statusParam&includes[]=cover_art&includes[]=author")

            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer ${appConfig.mangadexAccessToken}")

            val responseCode = connection.responseCode
            Log.d(TAG, "getUserReadingList response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(response)

                if (jsonObject.getString("result") == "ok") {
                    val mangaList = mutableListOf<Manga>()
                    val data = jsonObject.getJSONArray("data")

                    for (i in 0 until data.length()) {
                        val mangaObject = data.getJSONObject(i)
                        val manga = parseMangaFromJson(mangaObject)
                        mangaList.add(manga)
                    }

                    return@withContext mangaList
                }
            }

            return@withContext emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user reading list", e)
            return@withContext emptyList()
        }
    }

    /**
     * Add a manga to the user's favorites
     * @param mangaId The MangaDex manga ID
     * @return Boolean indicating success
     */
    suspend fun addMangaToFavorites(mangaId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isAuthenticated()) {
            Log.d(TAG, "addMangaToFavorites: Not authenticated")
            return@withContext false
        }

        try {
            val url = URL("$baseUrl/manga/$mangaId/follow")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer ${appConfig.mangadexAccessToken}")

            val responseCode = connection.responseCode
            Log.d(TAG, "addMangaToFavorites response code: $responseCode")

            return@withContext (responseCode == HttpURLConnection.HTTP_OK)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding manga to favorites", e)
            return@withContext false
        }
    }

    /**
     * Remove a manga from the user's favorites
     * @param mangaId The MangaDex manga ID
     * @return Boolean indicating success
     */
    suspend fun removeMangaFromFavorites(mangaId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isAuthenticated()) {
            Log.d(TAG, "removeMangaFromFavorites: Not authenticated")
            return@withContext false
        }

        try {
            val url = URL("$baseUrl/manga/$mangaId/follow")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "DELETE"
            connection.setRequestProperty("Authorization", "Bearer ${appConfig.mangadexAccessToken}")

            val responseCode = connection.responseCode
            Log.d(TAG, "removeMangaFromFavorites response code: $responseCode")

            return@withContext (responseCode == HttpURLConnection.HTTP_OK)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing manga from favorites", e)
            return@withContext false
        }
    }

    /**
     * Check if a manga is in the user's favorites
     * @param mangaId The MangaDex manga ID
     * @return Boolean indicating if manga is favorited
     */
    suspend fun isMangaInFavorites(mangaId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isAuthenticated()) {
            Log.d(TAG, "isMangaInFavorites: Not authenticated")
            return@withContext false
        }

        try {
            val url = URL("$baseUrl/manga/$mangaId/follow")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer ${appConfig.mangadexAccessToken}")

            val responseCode = connection.responseCode
            Log.d(TAG, "isMangaInFavorites response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(response)

                return@withContext jsonObject.getBoolean("isFollowing")
            }

            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if manga is in favorites", e)
            return@withContext false
        }
    }

    /**
     * Helper method to parse a manga object from JSON
     */
    private fun parseMangaFromJson(mangaObject: JSONObject): Manga {
        val id = mangaObject.getString("id")
        val attributes = mangaObject.getJSONObject("attributes")

        val titleObj = attributes.getJSONObject("title")
        val titles = mutableListOf<String>()
        for (key in titleObj.keys()) {
            titles.add(titleObj.getString(key))
        }

        val originalLanguage = attributes.getString("originalLanguage")

        val status = attributes.getString("status")

        val descriptionObj = attributes.optJSONObject("description")
        val description = if (descriptionObj != null) {
            descriptionObj.optString("en", descriptionObj.optString(descriptionObj.keys().next(), ""))
        } else ""

        val lastUpdated = attributes.optString("updatedAt")
        val contentRating = attributes.optString("contentRating", "safe")
        val year = attributes.optInt("year", 0)

        // Extract cover art
        var poster: String? = null
        val relationships = mangaObject.getJSONArray("relationships")
        for (i in 0 until relationships.length()) {
            val rel = relationships.getJSONObject(i)
            if (rel.getString("type") == "cover_art") {
                poster = rel.optJSONObject("attributes")?.optString("fileName")
                if (poster != null) {
                    poster = "https://uploads.mangadex.org/covers/$id/$poster"
                }
                break
            }
        }

        // Extract tags
        val tags = mutableListOf<MangaTag>()
        val tagsArray = attributes.optJSONArray("tags")
        if (tagsArray != null) {
            for (i in 0 until tagsArray.length()) {
                val tag = tagsArray.getJSONObject(i)
                val tagId = tag.getString("id")
                val tagName = tag.getJSONObject("attributes").getJSONObject("name").optString("en", "Unknown")
                tags.add(MangaTag(tagId, tagName))
            }
        }

        return Manga(
            id = id,
            title = titles,
            originalLanguage = originalLanguage,
            poster = poster,
            status = status,
            description = description,
            lastUpdated = lastUpdated,
            lastChapter = null, // These would need to be set separately
            latestUploadedChapterId = null,
            year = if (year == 0) null else year,
            contentRating = contentRating,
            tags = tags
        )
    }

    suspend fun getPopularManga(limit: Int = 20): Pair<Boolean, JSONObject?> = withContext(Dispatchers.IO) {
        suspendCoroutine { continuation ->
            try {
                getContentRatingParams()
//                val url = URL("$baseUrl/manga?limit=$limit&order[followedCount]=desc&includes[]=cover_art$contentRatingParams")
                val url = URL("$baseUrl/manga?limit=$limit&order[followedCount]=desc&includes[]=cover_art&includes[]=author")

                Log.d(TAG, "Fetching popular manga with URL: $url")
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
                val contentRatingParams = getContentRatingParams()
                val url = URL("$baseUrl/manga?limit=$limit&offset=$offset&order[updatedAt]=desc&includes[]=cover_art$contentRatingParams")

                Log.d(TAG, "Fetching latest updates with URL: $url")
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

    suspend fun searchForManga(title: String, vararg args: Any): List<Map<String, Any>>? = withContext(Dispatchers.IO) {
        try {
            val encodedTitle = URLEncoder.encode(title, "UTF-8")
            val contentRatingParams = getContentRatingParams()
            val url = URL("$baseUrl/manga?title=$encodedTitle&limit=50&includes[]=cover_art&includes[]=author&includes[]=rating$contentRatingParams")

            Log.d(TAG, "Searching manga with URL: $url")
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
                    val contentRating = attributes.optString("contentRating", "unknown")
                    when (contentRating) {
                        "safe" -> "Safe"
                        "suggestive" -> "Suggestive"
                        "erotica" -> "Erotica"
                        "pornographic" -> "Pornographic"
                        else -> "Unknown"
                    }

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

                    val tags = mutableListOf<MangaTag>()
                    val tagsArray = attributes.getJSONArray("tags")
                    for (j in 0 until tagsArray.length()) {
                        val tag = tagsArray.getJSONObject(j)
                        val tagId = tag.getString("id")
                        val tagAttributes = tag.getJSONObject("attributes")
                        val tagName = tagAttributes.getJSONObject("name")
                        val nameEn = tagName.optString("en")
                        if (!nameEn.isNullOrEmpty()) {
                            tags.add(MangaTag(id = tagId, name = nameEn))
                        }
                    }


                    val mangaMap = mapOf(
                        "id" to mangaId,
                        "title" to title,
                        "poster" to (coverImage ?: ""),
                        "type" to status,
                        "genres" to tags,
                        "year" to year,
                        "contentRating" to contentRating
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

    /**
     * Helper function to generate content rating parameters for API calls
     * based on user preferences in AppConfig
     */
    private fun getContentRatingParams(): String {
        val enabledContentTypes = appConfig.contentFilters

        if (enabledContentTypes.isEmpty()) {
            // Default to safe if nothing is selected
            return "&contentRating[]=${AppConfig.CONTENT_FILTER_SAFE}"
        }

        val contentRatingBuilder = StringBuilder()
        enabledContentTypes.forEach { contentType ->
            contentRatingBuilder.append("&contentRating[]=$contentType")
        }

        Log.d(TAG, "Using content filters: $enabledContentTypes")
        return contentRatingBuilder.toString()
    }

    suspend fun getMangaDetails(mangaId: String): Manga? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching manga details for ID: $mangaId")
            val url = "https://api.mangadex.org/manga/$mangaId?includes[]=cover_art&includes[]=author&includes[]=artist&includes[]=tag"
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(response)
                val data = jsonObject.getJSONObject("data")
                val attributes = data.getJSONObject("attributes")

                // Extract title
                val titleObj = attributes.getJSONObject("title")
                val titles = mutableListOf<String>()
                for (key in titleObj.keys()) {
                    titleObj.getString(key)?.let { titles.add(it) }
                }

                // Get original language
                val originalLanguage = attributes.getString("originalLanguage")

                // Extract description
                val description = attributes.optJSONObject("description")?.let { descObj ->
                    descObj.optString("en") ?: run {
                        // If English description not available, get the first available description
                        for (key in descObj.keys()) {
                            val desc = descObj.optString(key)
                            if (desc.isNotEmpty()) return@run desc
                        }
                        ""
                    }
                } ?: ""

                val status = attributes.optString("status", "unknown")
                val year = attributes.optInt("year", 0)
                val contentRating = attributes.optString("contentRating", "safe")

                // Get cover image
                var coverImage: String? = null
                val relationships = data.getJSONArray("relationships")
                for (i in 0 until relationships.length()) {
                    val rel = relationships.getJSONObject(i)
                    if (rel.getString("type") == "cover_art") {
                        if (rel.has("attributes")) {
                            val coverAttributes = rel.getJSONObject("attributes")
                            val fileName = coverAttributes.optString("fileName")
                            if (fileName.isNotEmpty()) {
                                coverImage = "https://uploads.mangadex.org/covers/$mangaId/$fileName"
                            }
                        }
                    }
                }

                // Extract tags
                val tagsArray = attributes.getJSONArray("tags")
                val tags = mutableListOf<MangaTag>()
                for (i in 0 until tagsArray.length()) {
                    val tag = tagsArray.getJSONObject(i)
                    val tagId = tag.getString("id")

                    // Extract tag name from attributes
                    val tagAttributes = tag.getJSONObject("attributes")
                    val tagNameObj = tagAttributes.getJSONObject("name")

                    // Try to get English name first, then fallback to any available language
                    val tagName = tagNameObj.optString("en") ?: run {
                        var name = ""
                        val keys = tagNameObj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val localizedName = tagNameObj.optString(key)
                            if (localizedName.isNotEmpty()) {
                                name = localizedName
                                break
                            }
                        }
                        name
                    }

                    // Only add tags with non-empty names
                    if (tagName.isNotEmpty()) {
                        tags.add(MangaTag(tagId, tagName))
                    }
                }

                return@withContext Manga(
                    id = mangaId,
                    title = titles,
                    originalLanguage = originalLanguage,
                    poster = coverImage,
                    status = status,
                    description = description,
                    lastUpdated = attributes.optString("updatedAt"),
                    lastChapter = null,
                    latestUploadedChapterId = attributes.optString("latestUploadedChapter"),
                    year = if (year > 0) year else null,
                    contentRating = contentRating,
                    tags = tags
                )
            }

            // Handle error responses
            val errorMessage = when(connection.responseCode) {
                404 -> "Manga not found"
                429 -> "Rate limit exceeded. Please try again later."
                500, 502, 503, 504 -> "Server error. Please try again later."
                else -> "Error ${connection.responseCode}: ${connection.responseMessage}"
            }

            Log.e(TAG, "API error: $errorMessage")
            throw IOException(errorMessage)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing manga details", e)
            return@withContext null
        }
    }

    suspend fun getChapters(mangaId: String): List<ChapterModel> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching chapters for manga ID: $mangaId")
            val chapters = mutableListOf<ChapterModel>()
            val chapterMap = mutableMapOf<String, MutableList<ChapterModel>>()
            var offset = 0
            var hasMoreChapters = true

            while (hasMoreChapters) {
                val url = "https://api.mangadex.org/manga/$mangaId/feed?limit=500&offset=$offset&includes[]=scanlation_group"
                Log.d(TAG, "Fetching chapters with offset: $offset")
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(response)
                    val dataArray = jsonObject.getJSONArray("data")

                    // If we got fewer than 500 chapters, we've reached the end
                    if (dataArray.length() < 500) {
                        hasMoreChapters = false
                    } else {
                        offset += 500
                    }

                    for (i in 0 until dataArray.length()) {
                        val chapterObject = dataArray.getJSONObject(i)
                        val chapterId = chapterObject.getString("id")
                        val attributes = chapterObject.getJSONObject("attributes")

                        val externalUrl = if (attributes.has("externalUrl") && !attributes.isNull("externalUrl"))
                            attributes.getString("externalUrl")
                        else
                            null

                        val isOfficial = !externalUrl.isNullOrBlank()

                        val chapterNumber = attributes.optString("chapter", "")
                        if (chapterNumber.isEmpty()) continue  // Skip chapters without numbers

                        // Get language code
                        val language = attributes.optString("translatedLanguage", "en")

                        // Get scanlation group
                        var translatorGroup: String? = null
                        val relationships = chapterObject.getJSONArray("relationships")
                        for (j in 0 until relationships.length()) {
                            val rel = relationships.getJSONObject(j)
                            if (rel.getString("type") == "scanlation_group" && rel.has("attributes")) {
                                translatorGroup = rel.getJSONObject("attributes").optString("name")
                                break
                            }
                        }

                        val chapterTitle = attributes.optString("title", "")
                        val finalTitle = if (chapterTitle.isBlank()) {
                            "Chapter $chapterNumber"
                        } else {
                            chapterTitle
                        }

                        val chapterModel = ChapterModel(
                            id = chapterId,
                            number = chapterNumber,
                            title = finalTitle,
                            publishedAt = attributes.optString("publishAt", ""),
                            pages = attributes.optInt("pages", 0),
                            volume = attributes.optString("volume", "Unknown"),
                            isRead = false,
                            language = language,
                            translatorGroup = translatorGroup,
                            languageFlag = getLanguageFlag(language),
                            isOfficial = isOfficial,
                            externalUrl = externalUrl
                        )

                        // Add to chapter map
                        if (!chapterMap.containsKey(chapterNumber)) {
                            chapterMap[chapterNumber] = mutableListOf()
                        }
                        chapterMap[chapterNumber]?.add(chapterModel)
                    }
                } else {
                    // Handle error responses
                    val errorMessage = when(connection.responseCode) {
                        404 -> "Chapters not found"
                        429 -> "Rate limit exceeded. Please try again later."
                        500, 502, 503, 504 -> "Server error. Please try again later."
                        else -> "Error ${connection.responseCode}: ${connection.responseMessage}"
                    }

                    Log.e(TAG, "API error: $errorMessage")
                    throw IOException(errorMessage)
                }
            }

            // Sort each chapter's translations by language (English first)
            chapterMap.forEach { (_, translations) ->
                translations.sortWith(compareBy<ChapterModel> { it.language != "en" }
                    .thenBy { it.publishedAt }
                    .thenBy { it.language })
            }

            // Flatten the map back to a list, sorted by chapter number
            val sortedChapterNumbers = chapterMap.keys
                .sortedWith(compareBy {
                    it.toFloatOrNull() ?: Float.MAX_VALUE
                })

            for (chapterNum in sortedChapterNumbers) {
                chapters.addAll(chapterMap[chapterNum] ?: emptyList())
            }

            Log.d(TAG, "Fetched a total of ${chapters.size} chapters for manga ID: $mangaId")
            return@withContext chapters

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching chapters", e)
            return@withContext emptyList()
        }
    }

    private fun getLanguageFlag(languageCode: String): String {
        // Extract the base language and region if present (e.g., "en-us" -> "en" and "us")
        val parts = languageCode.split("-")
        if (parts.size > 1) parts[1].uppercase() else ""

        // For languages with specific regions, use the region's flag
        return when (languageCode) {
            "en" -> "🇬🇧"  // English (default to UK)
            "en-us" -> "🇺🇸"  // English (US)
            "ja" -> "🇯🇵"  // Japanese
            "ko" -> "🇰🇷"  // Korean
            "zh" -> "🇨🇳"  // Chinese (simplified)
            "zh-hk", "zh-tw" -> "🇹🇼"  // Chinese (traditional)
            "ru" -> "🇷🇺"  // Russian
            "fr" -> "🇫🇷"  // French
            "de" -> "🇩🇪"  // German
            "es" -> "🇪🇸"  // Spanish
            "es-la" -> "🇲🇽"  // Spanish (Latin America)
            "pt-br" -> "🇧🇷"  // Portuguese (Brazil)
            "pt" -> "🇵🇹"  // Portuguese
            "it" -> "🇮🇹"  // Italian
            "pl" -> "🇵🇱"  // Polish
            "tr" -> "🇹🇷"  // Turkish
            "th" -> "🇹🇭"  // Thai
            "vi" -> "🇻🇳"  // Vietnamese
            "id" -> "🇮🇩"  // Indonesian
            "ar" -> "🇸🇦"  // Arabic
            "hi" -> "🇮🇳"  // Hindi
            "bn" -> "🇧🇩"  // Bengali
            "ms" -> "🇲🇾"  // Malay
            "fi" -> "🇫🇮"  // Finnish
            "da" -> "🇩🇰"  // Danish
            "no" -> "🇳🇴"  // Norwegian
            "sv" -> "🇸🇪"  // Swedish
            "cs" -> "🇨🇿"  // Czech
            "sk" -> "🇸🇰"  // Slovak
            "hu" -> "🇭🇺"  // Hungarian
            "ro" -> "🇷🇴"  // Romanian
            "uk" -> "🇺🇦"  // Ukrainian
            "bg" -> "🇧🇬"  // Bulgarian
            "el" -> "🇬🇷"  // Greek
            "he" -> "🇮🇱"  // Hebrew
            "lt" -> "🇱🇹"  // Lithuanian
            "lv" -> "🇱🇻"  // Latvian
            "et" -> "🇪🇪"  // Estonian
            "sl" -> "🇸🇮"  // Slovenian
            "hr" -> "🇭🇷"  // Croatian
            "sr" -> "🇷🇸"  // Serbian
            "mk" -> "🇲🇰"  // Macedonian
            "sq" -> "🇦🇱"  // Albanian
            "bs" -> "🇧🇦"  // Bosnian
            "is" -> "🇮🇸"  // Icelandic
            "ga" -> "🇮🇪"  // Irish
            "cy" -> "🇬🇧"  // Welsh
            "eu" -> "🇪🇸"  // Basque
            "ca" -> "🇪🇸"  // Catalan
            "sw" -> "🇰🇪"  // Swahili
            "tl" -> "🇵🇭"  // Tagalog
            "jw" -> "🇮🇩"  // Javanese
            "su" -> "🇮🇩"  // Sundanese
            "la" -> "🇻🇦"  // Latin
            "tlh" -> "🇰🇷"  // Klingon
            "xh" -> "🇿🇦"  // Xhosa
            "zu" -> "🇿🇦"  // Zulu
            "af" -> "🇿🇦"  // Afrikaans
            "am" -> "🇪🇹"  // Amharic
            "hy" -> "🇦🇲"  // Armenian
            "mn" -> "🇲🇳"  // Mongolian
            "ne" -> "🇳🇵"  // Nepali
            "pa" -> "🇮🇳"  // Punjabi
            "ta" -> "🇮🇳"  // Tamil
            "te" -> "🇮🇳"  // Telugu
            "ml" -> "🇮🇳"  // Malayalam
            "or" -> "🇮🇳"  // Odia
            "si" -> "🇱🇰"  // Sinhala
            "my" -> "🇲🇲"  // Burmese
            "km" -> "🇰🇭"  // Khmer
            "lo" -> "🇱🇦"  // Lao
            else -> "🌐 $languageCode"  // Globe and language code
        }
    }
}